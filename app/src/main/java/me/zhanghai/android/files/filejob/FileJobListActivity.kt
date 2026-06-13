/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.util.createIntent

class FileJobListActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure the fragment is added
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                add(android.R.id.content, FileJobListFragment())
            }
        }
    }

    companion object {
        fun createIntent(): Intent = createIntent<FileJobListActivity>()
    }
}