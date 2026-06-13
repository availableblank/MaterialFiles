/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FileJobListItemBinding
import me.zhanghai.android.files.ui.SimpleAdapter

class FileJobListAdapter : SimpleAdapter<FileJobState, FileJobListAdapter.ViewHolder>() {
    fun replaceList(list: List<FileJobState>) {
        replace(list)
    }

    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).jobId.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FileJobListItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val state = getItem(position)
        holder.bind(state)
    }

    class ViewHolder(
        private val binding: FileJobListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(state: FileJobState) {
            binding.titleText.text = state.title
            binding.fileNameText.text = state.currentFileName
            binding.progressText.text = if (!state.indeterminate && state.totalCount > 0) {
                "${state.completedCount}/${state.totalCount}"
            } else {
                ""
            }
            binding.progressBar.isIndeterminate = state.indeterminate
            if (!state.indeterminate && state.totalCount > 0) {
                binding.progressBar.max = state.totalCount
                binding.progressBar.progress = state.completedCount
            }
        }
    }
}