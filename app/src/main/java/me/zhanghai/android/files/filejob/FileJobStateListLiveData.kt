/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import androidx.lifecycle.MutableLiveData

object FileJobStateListLiveData : MutableLiveData<List<FileJobState>>() {
    private val _states = mutableListOf<FileJobState>()

    init {
        value = emptyList()
    }

    @Synchronized
    fun updateJobState(state: FileJobState) {
        val index = _states.indexOfFirst { it.jobId == state.jobId }
        if (state.isComplete || state.isFailed) {
            // Remove completed/failed jobs after a brief moment
            if (index != -1) {
                _states.removeAt(index)
            }
        } else {
            if (index != -1) {
                _states[index] = state
            } else {
                _states.add(state)
            }
        }
        postValue(_states.toList())
    }

    @Synchronized
    fun removeJobState(jobId: Int) {
        _states.removeAll { it.jobId == jobId }
        postValue(_states.toList())
    }

    @Synchronized
    fun clear() {
        _states.clear()
        postValue(emptyList())
    }
}