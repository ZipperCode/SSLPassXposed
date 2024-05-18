package com.zipper.sslpass.xposed.hooker

import com.zipper.sslpass.xposed.findClassIfExists
import com.zipper.sslpass.xposed.runCatch
import com.zipper.sslpass.xposed.utils.ILog
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.net.InetSocketAddress
import java.net.Proxy


object ProxyHooker : ILog {

    override val subTag: String
        get() = "ProxyHooker"

    fun hook() {
        val httpProxy = System.getProperty("http.proxyHost")
        val port = System.getProperty("https.proxyPort")
        debug("proxyHttp = %s, port = %s", httpProxy, port)
        if (httpProxy.isNullOrBlank() || port.isNullOrBlank()) {
            error("未设置代理")
            return
        }
        debug("before NO_PROXY = %s", Proxy.NO_PROXY)

        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(httpProxy, port.toInt()))
        runCatch {
            val field = Proxy::class.java.getDeclaredField("NO_PROXY")
            field.isAccessible = true
            field.set(null, proxy)
            debug("after NO_PROXY = %s", Proxy.NO_PROXY)
        }
    }
}