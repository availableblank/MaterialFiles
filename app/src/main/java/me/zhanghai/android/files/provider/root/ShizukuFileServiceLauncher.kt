/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.root

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.provider.remote.IRemoteFileService
import me.zhanghai.android.files.provider.remote.RemoteFileServiceInterface
import me.zhanghai.android.files.provider.remote.RemoteFileSystemException
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuFileServiceLauncher {
    private val lock = Any()
    private val LOG_TAG = ShizukuFileServiceLauncher::class.java.simpleName

    fun isShizukuAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        return try {
            // Shizuku v13: pingBinder() is deprecated and unreliable.
            // Use getBinder() instead. If Shizuku is running, it returns a non-null binder
            // even if the app hasn't been authorized yet.
            val binder = Shizuku.getBinder()
            Log.d(LOG_TAG, "Shizuku binder: ${binder != null}")
            binder != null
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Shizuku is not available", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(RemoteFileSystemException::class)
    fun launchService(): IRemoteFileService {
        synchronized(lock) {
            if (!isShizukuAvailable()) {
                throw RemoteFileSystemException("Shizuku isn't available")
            }
            val permission = Shizuku.checkSelfPermission()
            Log.d(LOG_TAG, "Shizuku permission: $permission")
            if (permission != PackageManager.PERMISSION_GRANTED) {
                val granted = try {
                    runBlocking<Boolean> {
                        suspendCancellableCoroutine { continuation ->
                            val listener = object : Shizuku.OnRequestPermissionResultListener {
                                override fun onRequestPermissionResult(
                                    requestCode: Int,
                                    grantResult: Int
                                ) {
                                    Shizuku.removeRequestPermissionResultListener(this)
                                    Log.d(LOG_TAG, "Shizuku permission result: $grantResult")
                                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                                    continuation.resume(granted)
                                }
                            }
                            Shizuku.addRequestPermissionResultListener(listener)
                            continuation.invokeOnCancellation {
                                Shizuku.removeRequestPermissionResultListener(listener)
                            }
                            Shizuku.requestPermission(0)
                        }
                    }
                } catch (e: InterruptedException) {
                    throw RemoteFileSystemException(e)
                }
                if (!granted) {
                    throw RemoteFileSystemException("Shizuku permission isn't granted")
                }
            }
            return try {
                runBlocking {
                    try {
                        withTimeout(RootFileService.TIMEOUT_MILLIS) {
                            suspendCancellableCoroutine { continuation ->
                                val serviceArgs = Shizuku.UserServiceArgs(
                                    ComponentName(
                                        application,
                                        ShizukuFileServiceInterface::class.java
                                    )
                                )
                                    .debuggable(BuildConfig.DEBUG)
                                    .daemon(false)
                                    .processNameSuffix("shizuku")
                                    .version(BuildConfig.VERSION_CODE)
                                val connection = object : ServiceConnection {
                                    override fun onServiceConnected(
                                        name: ComponentName,
                                        service: IBinder
                                    ) {
                                        val serviceInterface =
                                            IRemoteFileService.Stub.asInterface(service)
                                        Log.d(LOG_TAG, "Shizuku user service connected")
                                        continuation.resume(serviceInterface)
                                    }

                                    override fun onServiceDisconnected(name: ComponentName) {
                                        if (continuation.isActive) {
                                            Log.w(LOG_TAG, "Shizuku user service disconnected")
                                            continuation.resumeWithException(
                                                RemoteFileSystemException(
                                                    "Shizuku service disconnected"
                                                )
                                            )
                                        }
                                    }

                                    override fun onBindingDied(name: ComponentName) {
                                        if (continuation.isActive) {
                                            Log.w(LOG_TAG, "Shizuku binding died")
                                            continuation.resumeWithException(
                                                RemoteFileSystemException(
                                                    "Shizuku binding died"
                                                )
                                            )
                                        }
                                    }

                                    override fun onNullBinding(name: ComponentName) {
                                        if (continuation.isActive) {
                                            Log.w(LOG_TAG, "Shizuku null binding")
                                            continuation.resumeWithException(
                                                RemoteFileSystemException(
                                                    "Shizuku binding is null"
                                                )
                                            )
                                        }
                                    }
                                }
                                Shizuku.bindUserService(serviceArgs, connection)
                                continuation.invokeOnCancellation {
                                    Shizuku.unbindUserService(serviceArgs, connection, true)
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(LOG_TAG, "Shizuku user service timed out")
                        throw RemoteFileSystemException(e)
                    }
                }
            } catch (e: InterruptedException) {
                throw RemoteFileSystemException(e)
            }
        }
    }
}

@Keep
@RequiresApi(Build.VERSION_CODES.M)
class ShizukuFileServiceInterface : RemoteFileServiceInterface() {
    init {
        Log.i("ShizukuFileService", "Initializing remote file service")
        RootFileService.main()
    }
}