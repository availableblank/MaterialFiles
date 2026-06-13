/*
 * Copyright (c) 2024 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filejob

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.valueCompat

class FileJobListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: FileJobListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate a layout that contains a RecyclerView and an empty view TextView.
        // Expected layout: R.layout.file_job_list_fragment
        //   <FrameLayout>
        //     <androidx.recyclerview.widget.RecyclerView android:id="@+id/recyclerView" />
        //     <TextView android:id="@+id/emptyView" android:text="@string/file_job_list_empty" />
        //   </FrameLayout>
        val view = inflater.inflate(R.layout.file_job_list_fragment, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val context = requireContext()
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = FileJobListAdapter()
        recyclerView.adapter = adapter

        FileJobStateListLiveData.observe(viewLifecycleOwner) { onJobStatesChanged(it) }
    }

    private fun onJobStatesChanged(states: List<FileJobState>) {
        adapter.replaceList(states)
        val hasJobs = states.isNotEmpty()
        recyclerView.visibility = if (hasJobs) View.VISIBLE else View.GONE
        emptyView.visibility = if (hasJobs) View.GONE else View.VISIBLE
    }
}