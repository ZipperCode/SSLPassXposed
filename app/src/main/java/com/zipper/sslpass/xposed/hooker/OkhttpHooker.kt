package com.zipper.sslpass.xposed.hooker

import com.zipper.sslpass.xposed.utils.ILog
import com.zipper.sslpass.xposed.findAndHookConstructorAfter
import com.zipper.sslpass.xposed.findAndHookMethodBefore
import com.zipper.sslpass.xposed.findAndHookMethodDoNothing
import com.zipper.sslpass.xposed.findAndHookMethodReplace
import com.zipper.sslpass.xposed.findClassIfExists
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.net.ssl.SSLSession


object OkhttpHooker : ILog {
    override val subTag: String
        get() = "OkhttpHooker"

    private val hookerMethodRecord: ConcurrentMap<String, Boolean> = ConcurrentHashMap()

    fun hook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        loadPackageParam.findAndHookConstructorAfter("javax.net.ssl.SSLPeerUnverifiedException", String::class.java) {
            val reason = it.args[0]
            if (reason == "Certificate pinning failure!") {
                val trace = Thread.currentThread().stackTrace
                error("SSLPeerUnverifiedException#\$init >> trace >>")
                for (stackTraceElement in trace) {
                    error("at ${stackTraceElement.fileName}_${stackTraceElement.className}_${stackTraceElement.methodName}")
                }
            }
        }
        val certificatePinnerCls = loadPackageParam.findClassIfExists("com.squareup.okhttp.CertificatePinner")
        findAndHookMethodReplace(
            certificatePinnerCls, "check",
            String::class.java,
            MutableList::class.java
        ) {
            return@findAndHookMethodReplace true
        }

        findAndHookMethodDoNothing(
            certificatePinnerCls, "check",
            String::class.java,
            MutableList::class.java,
        )


        val okHostnameVerifierCls = loadPackageParam.findClassIfExists("okhttp3.internal.tls.OkHostnameVerifier")

        findAndHookMethodReplace(
            okHostnameVerifierCls, "verify",
            String::class.java,
            SSLSession::class.java,
        ) {
            return@findAndHookMethodReplace true
        }

        findAndHookMethodReplace(
            okHostnameVerifierCls, "verify",
            String::class.java,
            X509Certificate::class.java,
        ) {
            return@findAndHookMethodReplace true
        }


        val certPinerClass = XposedHelpers.findClassIfExists("okhttp3.CertificatePinner", loadPackageParam.classLoader)
        findAndHookMethodBefore(
            certPinerClass,
            "findMatchingPins",
            String::class.java,
        ) {
            debug("okhttp3.CertificatePinner#findMatchingPins >> arg0 = %s", it.args)
            it.args[0] = ""
        }

        findAndHookMethodDoNothing(
            certPinerClass,
            "check\$okhttp",
            String::class.java,
            loadPackageParam.findClassIfExists("kotlin.jvm.functions.Function0")
        )

        findAndHookMethodDoNothing(
            certPinerClass, "check",
            String::class.java,
            MutableList::class.java
        )
    }


}