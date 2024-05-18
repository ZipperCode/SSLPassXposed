package com.zipper.sslpass.xposed.hooker

import android.app.Application
import android.util.Log
import com.zipper.sslpass.xposed.findAndHookMethodAfter
import com.zipper.sslpass.xposed.findAndHookMethodBefore
import com.zipper.sslpass.xposed.findAndHookMethodDoNothing
import com.zipper.sslpass.xposed.findAndHookMethodReplace
import com.zipper.sslpass.xposed.findClassIfExists
import com.zipper.sslpass.xposed.utils.ILog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.InputStream
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine


object TrustHooker : ILog {

    override val subTag: String
        get() = "TrustHooker"


    fun hook(loadPackageParam: LoadPackageParam) {
        JustTrustMeHooker.hook(loadPackageParam)
        TrustManagerImplHooker.hook(loadPackageParam)
        networkConfigTrustHook(loadPackageParam)
        ProxyHooker.hook()

        findAndHookMethodAfter(Application::class.java, "onCreate") {
            val application = it.thisObject as Application
            JustTrustMeHooker.onApplicationCreate(application)
            OkhttpHooker.hook(loadPackageParam)
        }

//        val openSSLSocketFactoryImplCls = loadPackageParam.findClassIfExists("com.android.org.conscrypt.OpenSSLSocketFactoryImpl")
//        debug("openSSLSocketFactoryImplCls = %s", openSSLSocketFactoryImplCls)
//        if (openSSLSocketFactoryImplCls != null) {
//
//            for (declaredMethod in openSSLSocketFactoryImplCls.declaredMethods) {
//                findAndHookMethodAfter(openSSLSocketFactoryImplCls, declaredMethod.name, *declaredMethod.parameterTypes) {
//                    error("OpenSSLSocketFactoryImpl#%s_%s = %s", declaredMethod.name, declaredMethod.parameterTypes, Log.getStackTraceString(Throwable()))
//                }
//            }
//
//        }

//        for (declaredMethod in KeyStore::class.java.declaredMethods) {
//            if (declaredMethod.name == "load") {
//                debug("拦截load方法 > %s ", declaredMethod.parameterTypes.map { it.toString() })
//                findAndHookMethodReplace(KeyStore::class.java, "load", *declaredMethod.parameterTypes) {
//                    debug("load Hook >>%s", declaredMethod.parameterTypes.map { it.toString() })
//                    if (it.args[0] is InputStream) {
//
//                        val inputStream = it.args[0] as? InputStream
//                        if (inputStream != null) {
//                            inputStream.mark(0)
//                            val text = inputStream.reader().readText()
//                            inputStream.reset()
//                            error("加载的证书 = %s", text)
//                        }
//                    }
//                }
//            }
//        }
    }


    private fun networkConfigTrustHook(loadPackageParam: LoadPackageParam) {
        val networkSecretTrustManagerCls = loadPackageParam.findClassIfExists("android.security.net.config.NetworkSecurityTrustManager")
        error("networkSecretTrustManagerCls = %s", networkSecretTrustManagerCls)
        if (networkSecretTrustManagerCls != null) {
            // findAndHookMethodDoNothing(networkSecretTrustManagerCls, "checkPins")

            findAndHookMethodDoNothing(
                networkSecretTrustManagerCls,
                "checkServerTrusted",
                Array<X509Certificate>::class.java,
                String::class.java
            )
            findAndHookMethodDoNothing(
                networkSecretTrustManagerCls,
                "checkClientTrusted",
                Array<X509Certificate>::class.java,
                String::class.java,
                Socket::class.java
            )
            findAndHookMethodDoNothing(
                networkSecretTrustManagerCls,
                "checkClientTrusted",
                Array<X509Certificate>::class.java,
                String::class.java,
                SSLEngine::class.java
            )

            findAndHookMethodDoNothing(
                networkSecretTrustManagerCls,
                "checkServerTrusted",
                Array<X509Certificate>::class.java,
                String::class.java
            )
            findAndHookMethodDoNothing(
                networkSecretTrustManagerCls,
                "checkServerTrusted",
                Array<X509Certificate>::class.java,
                String::class.java,
                Socket::class.java
            )
            findAndHookMethodDoNothing(
                networkSecretTrustManagerCls,
                "checkServerTrusted",
                Array<X509Certificate>::class.java,
                String::class.java,
                SSLEngine::class.java
            )
            findAndHookMethodReplace(
                networkSecretTrustManagerCls,
                "checkServerTrusted",
                Array<X509Certificate>::class.java,
                String::class.java,
                String::class.java
            ) {
                return@findAndHookMethodReplace ArrayList<X509Certificate>()
            }

            findAndHookMethodDoNothing(networkSecretTrustManagerCls, "checkPins", List::class.java)
        }
    }
}