package com.zipper.sslpass.xposed.hooker

import com.zipper.sslpass.xposed.utils.ILog
import com.zipper.sslpass.xposed.findAndHookMethodReplace
import com.zipper.sslpass.xposed.findClassIfExists
import com.zipper.sslpass.xposed.runCatch
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.security.cert.X509Certificate
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession

object TrustManagerImplHooker : ILog {

    override val subTag: String
        get() = "TrustManagerImplHooker"

    fun hook(loadPackageParam: LoadPackageParam) {
        /* TrustManagerImpl Hooks */
        /* external/conscrypt/src/platform/java/org/conscrypt/TrustManagerImpl.java */
        /* public void checkServerTrusted(X509Certificate[] chain, String authType) */
        loadPackageParam.findAndHookMethodReplace(
            "com.android.org.conscrypt.TrustManagerImpl", "checkServerTrusted",
            Array<X509Certificate>::class.java,
            String::class.java,
        ) {
            return@findAndHookMethodReplace 0
        }

        /* public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                String authType, String host) throws CertificateException */

        loadPackageParam.findAndHookMethodReplace(
            "com.android.org.conscrypt.TrustManagerImpl", "checkServerTrusted",
            Array<X509Certificate>::class.java, String::class.java,
            String::class.java
        ) {
            return@findAndHookMethodReplace ArrayList<X509Certificate>()
        }

        /* public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,
                                String authType, SSLSession session) throws CertificateException */
        loadPackageParam.findAndHookMethodReplace(
            "com.android.org.conscrypt.TrustManagerImpl", "checkServerTrusted",
            Array<X509Certificate>::class.java, String::class.java,
            SSLSession::class.java
        ) {
            return@findAndHookMethodReplace ArrayList<X509Certificate>()
        }
        loadPackageParam.findAndHookMethodReplace(
            "com.android.org.conscrypt.TrustManagerImpl", "checkTrusted",
            Array<X509Certificate>::class.java,
            String::class.java,
            SSLSession::class.java,
            SSLParameters::class.java,
            Boolean::class.javaPrimitiveType
        ) {
            return@findAndHookMethodReplace ArrayList<X509Certificate>()
        }
        loadPackageParam.findAndHookMethodReplace(
            "com.android.org.conscrypt.TrustManagerImpl", "checkTrusted",
            Array<X509Certificate>::class.java,
            ByteArray::class.java,
            ByteArray::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType
        ) {
            return@findAndHookMethodReplace ArrayList<X509Certificate>()
        }

        checkTrustedRecursiveHook(loadPackageParam)

    }

    private fun checkTrustedRecursiveHook(loadPackageParam: LoadPackageParam) = runCatch {
        val clazz = loadPackageParam.findClassIfExists("com.android.org.conscrypt.TrustManagerImpl")
        if (clazz == null) {
            error("checkTrustedRecursiveHook >> 失败，类不存在")
            return@runCatch
        }
        for (declaredMethod in clazz.declaredMethods) {
            if (declaredMethod.name != "checkTrustedRecursive") {
                continue
            }
            val paramTypes = declaredMethod.parameterTypes
            val retType = declaredMethod.returnType
            if (retType != List::class.java) {
                continue
            }
            runCatch {
                XposedHelpers.findAndHookMethod(clazz, declaredMethod.name, *paramTypes, object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam?): Any {
                        debug("TrustManagerImpl#checkTrustedRecursiveHook >> ")
                        return ArrayList<X509Certificate>()
                    }
                })
            }
        }
        XposedHelpers.findMethodsByExactParameters(clazz, List::class.java, String::class.java)
    }
}