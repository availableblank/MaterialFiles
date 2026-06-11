/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Intent
import android.os.Bundle
import java8.nio.file.Path
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.viewer.image.ImageViewerActivity
import me.zhanghai.android.files.viewer.text.TextEditorActivity

class OpenFileActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val path = intent.extraPath
        val mimeType = intent.type?.asMimeTypeOrNull()
        if (path != null && mimeType != null) {
            openFile(path, mimeType)
        }
        finish()
    }

    private fun openFile(path: Path, mimeType: MimeType) {
        when {
            path.isArchivePath -> {
                FileJobService.open(path, mimeType, false, this)
            }
            mimeType.isImageType() -> {
                openImage(path)
            }
            mimeType.isTextType() -> {
                openText(path)
            }
            else -> {
                val intent = path.fileProviderUri.createViewIntent(mimeType)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    .apply { extraPath = path }
                startActivitySafe(intent)
            }
        }
    }

    private fun openImage(path: Path) {
        val intent = Intent(this, ImageViewerActivity::class.java)
        ImageViewerActivity.putExtras(intent, listOf(path), 0)
        startActivity(intent)
    }

    private fun openText(path: Path) {
        val intent = Intent(this, TextEditorActivity::class.java)
            .apply { extraPath = path }
        startActivity(intent)
    }

    companion object {
        private const val ACTION_OPEN_FILE = "me.zhanghai.android.files.intent.action.OPEN_FILE"

        fun createIntent(path: Path, mimeType: MimeType): Intent =
            Intent(ACTION_OPEN_FILE)
                .setPackage(application.packageName)
                .setType(mimeType.value)
                .apply { extraPath = path }
    }
}

// MIME 类型判断扩展
private fun MimeType.isImageType(): Boolean = value.startsWith("image/")

private fun MimeType.isTextType(): Boolean =
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