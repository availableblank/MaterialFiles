/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import me.zhanghai.android.files.util.showToast
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Random

abstract class FileJob {
    val id = Random().nextInt()

    internal lateinit var service: FileJobService
        private set
	open val operationType: OperationType? = null

	fun updateJobState(title: String, currentFileName: String, completedCount: Int,
					   totalCount: Int, indeterminate: Boolean) {
		val type = operationType ?: OperationType.COPY
		val state = FileJobState(
			jobId = id,
			title = title,
			currentFileName = currentFileName,
			completedCount = completedCount,
			totalCount = totalCount,
			indeterminate = indeterminate,
			operationType = type
		)
		service.updateJobState(state)
	}

	fun runOn(service: FileJobService) {
		this.service = service
		try {
			run()
			val type = operationType ?: OperationType.COPY
			service.onJobCompleted(id, type)
		} catch (e: InterruptedIOException) {
			e.printStackTrace()
			val type = operationType ?: OperationType.COPY
			service.onJobFailed(id, type)
		} catch (e: Exception) {
			e.printStackTrace()
			service.showToast(e.toString())
			val type = operationType ?: OperationType.COPY
			service.onJobFailed(id, type)
		} finally {
			service.notificationManager.cancel(id)
		}
	}

    @Throws(IOException::class)
    protected abstract fun run()
}
