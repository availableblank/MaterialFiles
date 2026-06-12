/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class OpenUnknownFileDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val items = arrayOf(
            getString(R.string.file_open_unknown_image_viewer),
            getString(R.string.file_open_unknown_text_editor),
            getString(R.string.file_open_unknown_archive_viewer),
            getString(R.string.file_open_unknown_external_app)
        )
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_open_unknown_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> listener.openUnknownWithImageViewer(args.file)
                    1 -> listener.openUnknownWithTextEditor(args.file)
                    2 -> listener.openUnknownWithArchiveViewer(args.file)
                    3 -> listener.openUnknownWithExternalApp(args.file)
                }
            }
            .create()
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            OpenUnknownFileDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun openUnknownWithImageViewer(file: FileItem)
        fun openUnknownWithTextEditor(file: FileItem)
        fun openUnknownWithArchiveViewer(file: FileItem)
        fun openUnknownWithExternalApp(file: FileItem)
    }
}