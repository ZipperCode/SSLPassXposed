package com.zipper.sslpass.xposed

import com.zipper.sslpass.xposed.hooker.TrustHooker
import com.zipper.sslpass.xposed.utils.Logger
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedHookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val packageName = lpparam?.packageName ?: return
        val processName = lpparam.processName
        val classLoader = lpparam.classLoader
        Logger.d("Entry", "handleLoadPackage $packageName $processName $classLoader")

        TrustHooker.hook(lpparam)
    }
}