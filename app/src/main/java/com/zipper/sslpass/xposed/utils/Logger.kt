package com.zipper.sslpass.xposed.utils

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 *
 * @author zhangzhipeng
 * @date 2023/10/17
 */
object Logger {

    private const val MAIN_TAG = "SSLPass"

    fun i(msg: String, vararg varargs: Any?) {
        Log.i(MAIN_TAG, msg.format(*varargs))
    }

    fun i(tag: String, msg: String, vararg varargs: Any?) {
        Log.i(MAIN_TAG, "[$tag]: $msg".format(*varargs))
    }

    fun d(tag: String, msg: String, vararg varargs: Any?) {
        Log.d(MAIN_TAG, "[$tag]: $msg".format(*varargs))
    }

    fun e(tag: String, msg: String, vararg varargs: Any?) {
        Log.e(MAIN_TAG, "[$tag]: $msg".format(*varargs))
    }

    fun printStackTrace(e: Throwable? = null) {
        Log.e(MAIN_TAG, Log.getStackTraceString(e ?: Throwable()))
    }

    fun log(msg: String, vararg varargs: Any?) {
        XposedBridge.log("$MAIN_TAG: ${msg.format(*varargs)}")
    }
}
