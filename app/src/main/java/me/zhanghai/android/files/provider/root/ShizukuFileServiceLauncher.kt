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
    private val LOG_TAG = "ShizukuFileService"

    fun isShizukuAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        return try {
            // Shizuku.pingBinder() requires ShizukuProvider in manifest to work.
            // It checks if the binder has been received and is alive.
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Shizuku not available", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    @Throws(RemoteFileSystemException::class)
    fun launchService(): IRemoteFileService {
        synchronized(lock) {
            // Re-check: pingBinder must be true (binder received and alive)
            if (!isShizukuAvailable()) {
                throw RemoteFileSystemException("Shizuku isn't available")
            }
            if (Shizuku.isPreV11()) {
                throw RemoteFileSystemException("Shizuku version is too old (pre-v11)")
            }
            // Request permission if not granted
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    throw RemoteFileSystemException(
                        "Shizuku permission was denied permanently"
                    )
                }
                val granted = try {
                    runBlocking<Boolean> {
                        suspendCancellableCoroutine { continuation ->
                            val listener = object : Shizuku.OnRequestPermissionResultListener {
                                override fun onRequestPermissionResult(
                                    requestCode: Int,
                                    grantResult: Int
                                ) {
                                    Shizuku.removeRequestPermissionResultListener(this)
                                    continuation.resume(
                                        grantResult == PackageManager.PERMISSION_GRANTED
                                    )
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
                    throw RemoteFileSystemException("Shizuku permission wasn't granted")
                }
            }
            // Start user service
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
                                        continuation.resume(
                                            IRemoteFileService.Stub.asInterface(service)
                                        )
                                    }

                                    override fun onServiceDisconnected(name: ComponentName) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                RemoteFileSystemException(
                                                    "Shizuku service disconnected"
                                                )
                                            )
                                        }
                                    }

                                    override fun onBindingDied(name: ComponentName) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                RemoteFileSystemException(
                                                    "Shizuku binding died"
                                                )
                                            )
                                        }
                                    }

                                    override fun onNullBinding(name: ComponentName) {
                                        if (continuation.isActive) {
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
        RootFileService.main()
    }
}