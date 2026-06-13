/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.root

import android.annotation.SuppressLint
import android.content.Context
import android.os.Process
import android.util.Log
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.provider.FileSystemProviders
import me.zhanghai.android.files.provider.remote.RemoteFileService
import me.zhanghai.android.files.provider.remote.RemoteInterface
import me.zhanghai.android.files.provider.remote.RemoteFileSystemException
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.lazyReflectedMethod
import me.zhanghai.android.files.util.valueCompat

val isRunningAsRoot = Process.myUid() == 0

@Volatile
var isInRemoteProcess = false
    private set

@SuppressLint("StaticFieldLeak")
lateinit var rootContext: Context private set

object RootFileService : RemoteFileService(
    RemoteInterface {
        val strategy = if (isInRemoteProcess) RootStrategy.NEVER
                       else Settings.ROOT_STRATEGY.valueCompat
        when (strategy) {
            RootStrategy.NEVER -> throw RemoteFileSystemException(
                "Root access is disabled"
            )
            RootStrategy.SHIZUKU -> {
                if (ShizukuFileServiceLauncher.isShizukuAvailable()) {
                    ShizukuFileServiceLauncher.launchService()
                } else {
                    throw RemoteFileSystemException("Shizuku isn't available")
                }
            }
            RootStrategy.ALWAYS -> {
                when {
                    SuiFileServiceLauncher.isSuiAvailable() ->
                        SuiFileServiceLauncher.launchService()
                    LibSuFileServiceLauncher.isSuAvailable() ->
                        LibSuFileServiceLauncher.launchService()
                    else -> throw RemoteFileSystemException("Root isn't available")
                }
            }
            RootStrategy.AUTOMATIC -> {
                when {
                    SuiFileServiceLauncher.isSuiAvailable() ->
                        SuiFileServiceLauncher.launchService()
                    LibSuFileServiceLauncher.isSuAvailable() ->
                        LibSuFileServiceLauncher.launchService()
                    ShizukuFileServiceLauncher.isShizukuAvailable() ->
                        ShizukuFileServiceLauncher.launchService()
                    else -> throw RemoteFileSystemException(
                        "Neither root nor Shizuku is available"
                    )
                }
            }
        }
    }
) {
    const val TIMEOUT_MILLIS = 15 * 1000L

    private val LOG_TAG = RootFileService::class.java.simpleName

    private val activityThreadCurrentActivityThreadMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "currentActivityThread"
    )
    private val activityThreadGetSystemContextMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "getSystemContext"
    )

    fun main() {
        isInRemoteProcess = true
        Log.i(LOG_TAG, "Creating package context")
        rootContext = createPackageContext(BuildConfig.APPLICATION_ID)
        Log.i(LOG_TAG, "Installing file system providers")
        FileSystemProviders.install()
        FileSystemProviders.overflowWatchEvents = true
    }

    private fun createPackageContext(packageName: String): Context {
        val activityThread = activityThreadCurrentActivityThreadMethod.invoke(null)
        val systemContext = activityThreadGetSystemContextMethod.invoke(activityThread) as Context
        return systemContext.createPackageContext(
            packageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
    }
}