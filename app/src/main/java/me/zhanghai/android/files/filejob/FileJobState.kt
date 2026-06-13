/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileJobState(
    val jobId: Int,
    val title: String,
    val currentFileName: String = "",
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val indeterminate: Boolean = true,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val operationType: OperationType = OperationType.COPY
) : Parcelable {
    companion object {
        fun createInitial(jobId: Int, title: String, operationType: OperationType): FileJobState =
            FileJobState(
                jobId = jobId,
                title = title,
                indeterminate = true,
                operationType = operationType
            )

        fun createComplete(jobId: Int, operationType: OperationType): FileJobState =
            FileJobState(
                jobId = jobId,
                title = "",
                isComplete = true,
                operationType = operationType
            )

        fun createFailed(jobId: Int, operationType: OperationType): FileJobState =
            FileJobState(
                jobId = jobId,
                title = "",
                isFailed = true,
                operationType = operationType
            )
    }
}

enum class OperationType {
    COPY,
    MOVE,
    DELETE,
    ARCHIVE,
    EXTRACT
}