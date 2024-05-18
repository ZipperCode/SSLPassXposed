package com.zipper.sslpass.xposed.hooker

import android.content.Context
import android.net.http.SslError
import android.net.http.X509TrustManagerExtensions
import android.webkit.SslErrorHandler
import android.webkit.WebView
import com.zipper.sslpass.xposed.utils.ILog
import com.zipper.sslpass.xposed.TrustAllSSLSocketFactory
import com.zipper.sslpass.xposed.TrustAllX509ExtendedTrustManager
import com.zipper.sslpass.xposed.TrustHostnameVerifier
import com.zipper.sslpass.xposed.findAndHookConstructorAfter
import com.zipper.sslpass.xposed.findAndHookMethodAfter
import com.zipper.sslpass.xposed.findAndHookMethodBefore
import com.zipper.sslpass.xposed.findAndHookMethodDoNothing
import com.zipper.sslpass.xposed.findAndHookMethodReplace
import com.zipper.sslpass.xposed.findClassIfExists
import de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.callStaticMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.setObjectField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import org.apache.http.conn.ClientConnectionManager
import org.apache.http.conn.scheme.HostNameResolver
import org.apache.http.conn.scheme.PlainSocketFactory
import org.apache.http.conn.scheme.Scheme
import org.apache.http.conn.scheme.SchemeRegistry
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.SingleClientConnManager
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.params.HttpParams
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


object JustTrustMeHooker : ILog {

    override val subTag: String
        get() = "JustTrustMe"

    private var currentPackageName = ""

    fun hook(loadPackageParam: LoadPackageParam) {
        currentPackageName = loadPackageParam.packageName
        hookApacheHttpClient(loadPackageParam)

        findAndHookMethodReplace(
            X509TrustManagerExtensions::class.java, "checkServerTrusted",
            Array<X509Certificate>::class.java, String::class.java, String::class.java
        ) {
            return@findAndHookMethodReplace it.args[0]
        }

        loadPackageParam.findAndHookMethodDoNothing(
            "android.security.net.config.NetworkSecurityTrustManager",
            "checkPins",
            MutableList::class.java
        )

        /* JSSE Hooks */
        /* libcore/luni/src/main/java/javax/net/ssl/TrustManagerFactory.java */
        /* public final TrustManager[] getTrustManager() */
        debug("Hooking TrustManagerFactory.getTrustManagers() for: $currentPackageName")
        loadPackageParam.findAndHookMethodAfter("javax.net.ssl.TrustManagerFactory", "getTrustManagers") {
            val originResult = it.result as? Array<*>
            val cls = loadPackageParam.findClassIfExists("com.android.org.conscrypt.TrustManagerImpl")
            if (cls != null) {
                val managers = it.result as? Array<*>
                if (managers?.isNotEmpty() == true && cls.isInstance(managers[0])) {
                    return@findAndHookMethodAfter
                }
            }

            it.result = arrayOf<TrustManager>(getTrustManager())
        }

        hookHttpConnection(loadPackageParam)
        hookWebClient(loadPackageParam)


        //SSLContext.init >> (null,ImSureItsLegitTrustManager,null)
        loadPackageParam.findAndHookMethodBefore(
            "javax.net.ssl.SSLContext",
            "init",
            Array<KeyManager>::class.java,
            Array<TrustManager>::class.java,
            SecureRandom::class.java
        ) {
            it.args[0] = null
            it.args[1] = arrayOf<TrustManager>(getTrustManager())
            it.args[2] = null
        }

        // TrustManagerImplHook
    }

    fun onApplicationCreate(context: Context) {
        processHttpClientAndroidLib(context.classLoader)
        processXutils(context.classLoader)
    }

    private fun processHttpClientAndroidLib(classLoader: ClassLoader) {
        /* httpclientandroidlib Hooks */
        /* public final void verify(String host, String[] cns, String[] subjectAlts, boolean strictWithSubDomains) throws SSLException */
        debug("Hooking AbstractVerifier.verify(String, String[], String[], boolean) for: $currentPackageName")

        try {
            classLoader.loadClass("ch.boye.httpclientandroidlib.conn.ssl.AbstractVerifier")
            findAndHookMethod(
                "ch.boye.httpclientandroidlib.conn.ssl.AbstractVerifier", classLoader, "verify",
                String::class.java, Array<String>::class.java, Array<String>::class.java, Boolean::class.javaPrimitiveType,
                DO_NOTHING
            )
        } catch (e: ClassNotFoundException) {
            // pass
            debug("httpclientandroidlib not found in $currentPackageName-- not hooking")
        }
    }

    private fun processXutils(classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists("org.xutils.http.RequestParams", classLoader) ?: return
        debug("Hooking org.xutils.http.RequestParams.setSslSocketFactory(SSLSocketFactory) (3) for: $currentPackageName")
        findAndHookMethodBefore(cls, "setSslSocketFactory", javax.net.ssl.SSLSocketFactory::class.java) {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(getTrustManager()), null)
            it.args[0] = sslContext.socketFactory
        }

        findAndHookMethodBefore(cls, "setHostnameVerifier", HostnameVerifier::class.java) {
            it.args[0] = TrustHostnameVerifier()
        }

    }

    private fun hookWebClient(loadPackageParam: LoadPackageParam) {

        val webViewClientClazz = loadPackageParam.findClassIfExists("android.webkit.WebViewClient") ?: return
        /* WebView Hooks */
        /* frameworks/base/core/java/android/webkit/WebViewClient.java */
        /* public void onReceivedSslError(Webview, SslErrorHandler, SslError) */
        debug("Hooking WebViewClient.onReceivedSslError(WebView, SslErrorHandler, SslError) for: $currentPackageName")

        findAndHookMethodReplace(
            webViewClientClazz, "onReceivedSslError",
            WebView::class.java, SslErrorHandler::class.java, SslError::class.java
        ) {
            (it.args[1] as? SslErrorHandler)?.proceed()
            return@findAndHookMethodReplace null
        }


        /* frameworks/base/core/java/android/webkit/WebViewClient.java */
        /* public void onReceivedError(WebView, int, String, String) */
        debug("Hooking WebViewClient.onReceivedSslError(WebView, int, string, string) for: $currentPackageName")

        findAndHookMethodDoNothing(
            webViewClientClazz, "onReceivedError",
            WebView::class.java, Int::class.javaPrimitiveType, String::class.java, String::class.java
        )
    }

    private fun hookHttpConnection(loadPackageParam: LoadPackageParam) {
        /* libcore/luni/src/main/java/javax/net/ssl/HttpsURLConnection.java */
        /* public void setDefaultHostnameVerifier(HostnameVerifier) */
        debug("Hooking HttpsURLConnection.setDefaultHostnameVerifier for: $currentPackageName")

        loadPackageParam.findAndHookMethodDoNothing(
            "javax.net.ssl.HttpsURLConnection", "setDefaultHostnameVerifier",
            HostnameVerifier::class.java
        )


        /* libcore/luni/src/main/java/javax/net/ssl/HttpsURLConnection.java */
        /* public void setSSLSocketFactory(SSLSocketFactory) */
        debug("Hooking HttpsURLConnection.setSSLSocketFactory for: $currentPackageName")
        loadPackageParam.findAndHookMethodDoNothing(
            "javax.net.ssl.HttpsURLConnection", "setSSLSocketFactory",
            javax.net.ssl.SSLSocketFactory::class.java,
        )


        /* libcore/luni/src/main/java/javax/net/ssl/HttpsURLConnection.java */
        /* public void setHostnameVerifier(HostNameVerifier) */
        debug("Hooking HttpsURLConnection.setHostnameVerifier for: $currentPackageName")
        loadPackageParam.findAndHookMethodDoNothing(
            "javax.net.ssl.HttpsURLConnection", "setHostnameVerifier",
            HostnameVerifier::class.java,
        )

        hookApacheSSlSocketFactory(loadPackageParam)
    }

    private fun hookApacheSSlSocketFactory(loadPackageParam: LoadPackageParam) {
        val sslSocketFactoryClazz = loadPackageParam.findClassIfExists("org.apache.http.conn.ssl.SSLSocketFactory") ?: return
        debug("Hooking SSLSocketFactory(String, KeyStore, String, KeyStore) for: $currentPackageName")
        loadPackageParam.findAndHookConstructorAfter(
            sslSocketFactoryClazz,
            String::class.java, KeyStore::class.java, String::class.java, KeyStore::class.java,
            SecureRandom::class.java, HostNameResolver::class.java
        ) {
            val algorithm = it.args[0] as String
            val keystore = it.args[1] as? KeyStore
            val keystorePassword = it.args[2] as? String
            val random = it.args[4] as? SecureRandom
            var keyManagers: Any? = null
            if (keystore != null) {
                keyManagers = callStaticMethod(sslSocketFactoryClazz, "createKeyManagers", keystore, keystorePassword)
            }
            val trustManager = arrayOf(getTrustManager())

            val newSSLContext = SSLContext.getInstance(algorithm)
            setObjectField(it.thisObject, "sslcontext", newSSLContext)
            callMethod(newSSLContext, "init", keyManagers, trustManager, random)
            val newFactory = callMethod(newSSLContext, "getSSLSocketFactory")
            setObjectField(it.thisObject, "socketfactory", newFactory)
        }
        debug("Hooking static SSLSocketFactory(String, KeyStore, String, KeyStore) for: $currentPackageName")
        findAndHookMethodReplace(sslSocketFactoryClazz, "getSocketFactory") {
            return@findAndHookMethodReplace XposedHelpers.newInstance(sslSocketFactoryClazz)
        }
        /* external/apache-http/src/org/apache/http/conn/ssl/SSLSocketFactory.java */
        /* public boolean isSecure(Socket) */
        debug("Hooking SSLSocketFactory(Socket) for: $currentPackageName")
        findAndHookMethodDoNothing(sslSocketFactoryClazz, "isSecure", Socket::class.java)
    }

    private fun hookApacheHttpClient(loadPackageParam: LoadPackageParam) {
        val httpClientCls = loadPackageParam.findClassIfExists("org.apache.http.impl.client.DefaultHttpClient") ?: return
        debug("Hooking DefaultHTTPClient for: $currentPackageName")
        findAndHookConstructorAfter(httpClientCls) {
            setObjectField(it.thisObject, "defaultParams", null)
            setObjectField(it.thisObject, "connManager", getSCCM())
        }

        findAndHookConstructorAfter(httpClientCls) {
            setObjectField(it.thisObject, "defaultParams", null)
            setObjectField(it.thisObject, "connManager", getSCCM())
        }

        /* external/apache-http/src/org/apache/http/impl/client/DefaultHttpClient.java */
        /* public DefaultHttpClient(HttpParams params) */
        debug("Hooking DefaultHTTPClient(HttpParams) for: $currentPackageName")
        findAndHookConstructorAfter(httpClientCls, HttpParams::class.java) {
            setObjectField(it.thisObject, "defaultParams", it.args[0] as HttpParams)
            setObjectField(it.thisObject, "connManager", getSCCM())
        }

        /* external/apache-http/src/org/apache/http/impl/client/DefaultHttpClient.java */
        /* public DefaultHttpClient(ClientConnectionManager conman, HttpParams params) */
        debug("Hooking DefaultHTTPClient(ClientConnectionManager, HttpParams) for: $currentPackageName")

        val clientConnectionManagerCls = loadPackageParam.findClassIfExists("org.apache.http.conn.ClientConnectionManager")
        if (clientConnectionManagerCls != null) {
            findAndHookConstructorAfter(httpClientCls, clientConnectionManagerCls, HttpParams::class.java) {
                val params = it.args[1] as HttpParams

                setObjectField(it.thisObject, "defaultParams", params)
                setObjectField(it.thisObject, "connManager", getCCM(it.args[0], params))
            }
        }
    }


    private fun getTrustManager(): X509TrustManager {

        return TrustAllX509ExtendedTrustManager()
    }


    private fun getCCM(o: Any, params: HttpParams?): ClientConnectionManager? {
        val className = o.javaClass.simpleName

        if (className == "SingleClientConnManager") {
            return getSCCM()
        } else if (className == "ThreadSafeClientConnManager") {
            return getTSCCM(params)
        }

        return null
    }

    private fun getTSCCM(params: HttpParams?): ClientConnectionManager? {
        val trustStore: KeyStore
        try {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)

            val sf: SSLSocketFactory = TrustAllSSLSocketFactory(trustStore)
            sf.hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER

            val registry = SchemeRegistry()
            registry.register(Scheme("http", PlainSocketFactory.getSocketFactory(), 80))
            registry.register(Scheme("https", sf, 443))

            val ccm: ClientConnectionManager = ThreadSafeClientConnManager(params, registry)

            return ccm
        } catch (e: java.lang.Exception) {
            return null
        }
    }

    private fun getSCCM(): ClientConnectionManager? {
        val trustStore: KeyStore
        try {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)

            val sf: SSLSocketFactory = TrustAllSSLSocketFactory(trustStore)
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)

            val registry = SchemeRegistry()
            registry.register(Scheme("http", PlainSocketFactory.getSocketFactory(), 80))
            registry.register(Scheme("https", sf, 443))
            return SingleClientConnManager(null, registry)
        } catch (e: Exception) {
            return null
        }
    }


}

