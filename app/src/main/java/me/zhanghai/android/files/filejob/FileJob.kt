/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.annotation.StringRes
import me.zhanghai.android.files.compat.mainExecutorCompat
import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set

    fun runOn(service: FileJobService) {
        this.service = service
        try {
            run()
            val toastRes = completionToastRes
            if (toastRes != null) {
                service.mainExecutorCompat.execute {
                    service.showToast(
                        if (completionToastArgs.isNotEmpty())
                            service.getString(toastRes, *completionToastArgs)
                        else
                            service.getString(toastRes)
                    )
                }
            }
        } catch (e: InterruptedIOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
            service.showToast(e.toString())
        } finally {
            service.notificationManager.cancel(id)
        }
    }

    @Throws(IOException::class)
    protected abstract fun run()

    @StringRes
    protected open val completionToastRes: Int? = null

    protected open val completionToastArgs: Array<out Any?> = emptyArray()
}