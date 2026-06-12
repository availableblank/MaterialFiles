/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import me.zhanghai.android.files.viewer.image.ImageViewerActivity
import me.zhanghai.android.files.viewer.text.TextEditorActivity

// ── MIME 类型判断扩展（公开，供两处共用）───────────────────────────────

fun MimeType.isImageType(): Boolean = value.startsWith("image/")

fun MimeType.isTextType(): Boolean =
    value.startsWith("text/") ||
    value in listOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/xhtml+xml",
        "application/x-sh",
        "application/x-shellscript",
        "application/x-perl",
        "application/x-python",
        "application/x-httpd-php",
    )

// ── 文件打开器 ─────────────────────────────────────────────────────

object FileOpener {

    private const val IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX = 1000

    // ── 用于 OpenFileActivity 的简单入口 ──────────────────────────

    /**
     * 根据路径和 MIME 类型打开文件（无 adapter，无 chooser）。
     * 供 [OpenFileActivity] 调用。
     */
    fun openFile(path: Path, mimeType: MimeType, context: Context) {
        when {
            path.isArchivePath -> {
                FileJobService.open(path, mimeType, false, context)
            }
            mimeType.isImageType() -> {
                openImage(path, context)
            }
            mimeType.isTextType() -> {
                openText(path, context)
            }
            else -> {
                val intent = path.fileProviderUri.createViewIntent(mimeType)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    .apply { extraPath = path }
                context.startActivitySafe(intent)
            }
        }
    }

    /**
     * 用内置图片查看器打开单张图片。
     */
    fun openImage(path: Path, context: Context) {
        val intent = Intent(context, ImageViewerActivity::class.java)
        ImageViewerActivity.putExtras(intent, listOf(path), 0)
        context.startActivity(intent)
    }

    /**
     * 用内置文本编辑器打开文本文件。
     */
    fun openText(path: Path, context: Context) {
        val intent = Intent(context, TextEditorActivity::class.java)
            .apply { extraPath = path }
        context.startActivity(intent)
    }

    // ── 用于 FileListFragment 的入口（支持 adapter 和 chooser） ──

    /**
     * 根据文件类型路由打开方式，可选显示“打开方式”选择器，
     * 同时利用 [adapter] 为图片浏览提供相邻图片列表。
     * 供 [FileListFragment] 调用。
     */
    fun openFileWithIntent(
        path: Path,
        mimeType: MimeType,
        context: Context,
        adapter: FileListAdapter? = null,
        withChooser: Boolean = false
    ) {
        if (path.isArchivePath) {
            FileJobService.open(path, mimeType, withChooser, context)
            return
        }
        if (!withChooser) {
            if (mimeType.isImageType()) {
                openWithImageViewer(path, mimeType, adapter, context)
                return
            }
            if (mimeType.isTextType()) {
                openText(path, context)
                return
            }
        }
        val intent = path.fileProviderUri.createViewIntent(mimeType)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            .apply {
                extraPath = path
                if (adapter != null) {
                    putImageViewerExtras(this, path, mimeType, adapter)
                }
            }
            .let {
                if (withChooser) {
                    it.withChooser(
                        EditFileActivity::class.createIntent()
                            .putArgs(EditFileActivity.Args(path, mimeType)),
                        OpenFileAsDialogActivity::class.createIntent()
                            .putArgs(OpenFileAsDialogFragment.Args(path))
                    )
                } else {
                    it
                }
            }
        context.startActivitySafe(intent)
    }

    /**
     * 用内置图片查看器打开图片，并尽可能带上相邻图片以支持滑动浏览。
     */
    fun openWithImageViewer(
        targetPath: Path,
        mimeType: MimeType,
        adapter: FileListAdapter?,
        context: Context
    ) {
        if (adapter == null) {
            openImage(targetPath, context)
            return
        }

        var paths = mutableListOf<Path>()
        for (index in 0..<adapter.itemCount) {
            val file = adapter.getItem(index)
            val filePath = file.path
            if (file.mimeType.isImageType() || filePath == targetPath) {
                paths.add(filePath)
            }
        }
        var position = paths.indexOf(targetPath)
        if (position == -1) {
            openImage(targetPath, context)
            return
        }
        if (paths.size > IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX) {
            val start = (position - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX / 2)
                .coerceIn(0, paths.size - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            paths = paths.subList(start, start + IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            position -= start
        }
        val intent = Intent(context, ImageViewerActivity::class.java)
        ImageViewerActivity.putExtras(intent, paths, position)
        context.startActivity(intent)
    }

    /**
     * 为外部 Intent 附加 ImageViewerActivity 的相邻图片路径，
     * 用于通过“打开方式”分享图片时也能滑动浏览。
     */
    fun putImageViewerExtras(
        intent: Intent,
        targetPath: Path,
        mimeType: MimeType,
        adapter: FileListAdapter
    ) {
        if (!mimeType.isImageType()) {
            return
        }
        var paths = mutableListOf<Path>()
        for (index in 0..<adapter.itemCount) {
            val file = adapter.getItem(index)
            val filePath = file.path
            if (file.mimeType.isImageType() || filePath == targetPath) {
                paths.add(filePath)
            }
        }
        var position = paths.indexOf(targetPath)
        if (position == -1) {
            return
        }
        if (paths.size > IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX) {
            val start = (position - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX / 2)
                .coerceIn(0, paths.size - IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            paths = paths.subList(start, start + IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX)
            position -= start
        }
        ImageViewerActivity.putExtras(intent, paths, position)
    }
}