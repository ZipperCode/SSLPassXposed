package com.zipper.sslpass.xposed.utils

interface ILog {

    val subTag: String get() = "ILog"

    fun log(msg: String, vararg varargs: Any?) {
        Logger.log(msg, *varargs)
    }

    fun debug(msg: String, vararg varargs: Any?) {
        Logger.d(subTag, msg, *varargs)
    }

    fun error(msg: String, vararg varargs: Any?) {
        Logger.e(subTag, msg, *varargs)
    }

    fun printStackTrace(t: Throwable) {
        Logger.printStackTrace(t)
    }
}