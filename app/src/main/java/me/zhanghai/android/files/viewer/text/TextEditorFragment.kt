/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.TextEditorFragmentBinding
import me.zhanghai.android.files.ui.ThemedFastScroller
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.viewModels
import java.nio.charset.Charset

class TextEditorFragment : Fragment(), ConfirmReloadDialogFragment.Listener,
    ConfirmCloseDialogFragment.Listener {

    private val args by args<Args>()
    private lateinit var argsFile: Path

    private lateinit var binding: TextEditorFragmentBinding

    private lateinit var menuBinding: MenuBinding

    private val viewModel by viewModels { { TextEditorViewModel(argsFile) } }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var isSettingText = false

    // ──────────────────── 搜索状态 ────────────────────
    private val searchHelper = TextEditorSearchHelper()
    private var searchBarVisible = false
    // ──────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        lifecycleScope.launchWhenStarted {
            onBackPressedCallback = object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (searchBarVisible) {
                        closeSearch()
                    } else {
                        ConfirmCloseDialogFragment.show(this@TextEditorFragment)
                    }
                }
            }
            launch {
                viewModel.isTextChanged.collect {
                    onBackPressedCallback.isEnabled =
                        viewModel.isTextChanged.value || searchBarVisible
                }
            }
            addOnBackPressedCallback(onBackPressedCallback)

            launch { viewModel.encoding.collect { onEncodingChanged(it) } }
            launch { viewModel.textState.collect { onTextStateChanged(it) } }
            launch { viewModel.isTextChanged.collect { onIsTextChangedChanged(it) } }
            launch { viewModel.writeFileState.collect { onWriteFileStateChanged(it) } }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        TextEditorFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argsFile = args.intent.extraPath
        if (argsFile == null) {
            // TODO: Show a toast.
            finish()
            return
        }
        this.argsFile = argsFile

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        // TODO: Move reload-prevent here so that we can also handle save-as, etc. Or maybe just get
        //  rid of the mPathLiveData in TextEditorViewModel.
        ThemedFastScroller.create(binding.scrollView)
        // Manually save and restore state in view model to avoid TransactionTooLargeException.
        binding.textEdit.isSaveEnabled = false
        val textEditSavedState = viewModel.removeEditTextSavedState()
        if (textEditSavedState != null) {
            binding.textEdit.onRestoreInstanceState(textEditSavedState)
        }
        binding.textEdit.doAfterTextChanged {
            if (isSettingText) {
                return@doAfterTextChanged
            }
            // Might happen if the animation is running and user is quick enough.
            if (viewModel.textState.value !is DataState.Success) {
                return@doAfterTextChanged
            }
            // 用户修改了文本 → 清空搜索
            if (searchHelper.isActive) {
                clearSearchHighlights()
                searchHelper.clear()
                updateSearchMatchCount()
            }
            viewModel.isTextChanged.value = true
        }

        // ──────────── 搜索栏事件绑定 ────────────
        setupSearchBar()
        // ──────────────────────────────────────────
    }

    // ──────────────────── 搜索设置 ────────────────────
    private fun setupSearchBar() {
        // 关闭按钮
        binding.searchCloseButton.setOnClickListener { closeSearch() }

        // 文本变化监听
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                performSearch(s?.toString() ?: "")
            }
        })

        // 键盘搜索动作
        binding.searchEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(binding.searchEdit.text?.toString() ?: "")
                true
            } else false
        }

        // 导航按钮
        binding.searchPreviousButton.setOnClickListener { navigateMatch(forward = false) }
        binding.searchNextButton.setOnClickListener { navigateMatch(forward = true) }
    }

    private fun openSearch() {
        searchBarVisible = true
        binding.searchBar.fadeInUnsafe()
        binding.searchEdit.requestFocus()
        // 恢复上次搜索词（如有）
        if (searchHelper.isActive) {
            binding.searchEdit.setText(
                binding.searchEdit.text?.toString()?.ifEmpty { searchLastQuery }
            )
            binding.searchEdit.setSelection(binding.searchEdit.text?.length ?: 0)
        }
        onBackPressedCallback.isEnabled = true
    }

    private var searchLastQuery = ""

    private fun closeSearch() {
    searchBarVisible = false
    // 直接设置 GONE 而不依赖动画，确保布局立刻回收空间
    binding.searchBar.visibility = View.GONE
    binding.searchEdit.clearFocus()
    clearSearchHighlights()
    searchLastQuery = binding.searchEdit.text?.toString() ?: ""
    searchHelper.clear()
    updateSearchMatchCount()
    onBackPressedCallback.isEnabled = viewModel.isTextChanged.value
    // 强制父布局重新测量，消除空白残留
    binding.searchBar.requestLayout()
}

    private fun performSearch(query: String) {
        searchLastQuery = query
        val text = binding.textEdit.text ?: return
        val count = searchHelper.search(text, query)
        updateSearchMatchCount()

        if (count > 0) {
            applySearchHighlights()
            val match = searchHelper.currentMatch()
            if (match != null) {
                scrollToMatch(match)
            }
        } else {
            clearSearchHighlights()
        }
    }

    private fun navigateMatch(forward: Boolean) {
        val match = if (forward) searchHelper.nextMatch() else searchHelper.previousMatch()
        if (match != null) {
            applySearchHighlights()
            scrollToMatch(match)
            updateSearchMatchCount()
        }
    }

    private fun scrollToMatch(range: IntRange) {
        val editText = binding.textEdit
        // 将光标移动到匹配起始位置，以便 EditText 自动滚动到对应行
        editText.setSelection(range.first)
        val layout = editText.layout ?: return
        val line = layout.getLineForOffset(range.first)
        val y = layout.getLineTop(line)
        binding.scrollView.smoothScrollTo(0, y.coerceAtLeast(0))
    }

	private fun applySearchHighlights() {
		val editable = binding.textEdit.text ?: return
		searchHelper.applyHighlights(editable)
	}

	private fun clearSearchHighlights() {
		val editable = binding.textEdit.text ?: return
		searchHelper.clearHighlights(editable)
	}

    private fun updateSearchMatchCount() {
        val count = searchHelper.matchCount
        binding.searchMatchCount.visibility = if (searchHelper.isActive) View.VISIBLE else View.GONE
        if (searchHelper.isActive) {
            if (count == 0) {
                binding.searchMatchCount.text = getString(R.string.search_no_matches)
            } else {
                binding.searchMatchCount.text = getString(
                    R.string.search_match_count_format,
                    searchHelper.currentIndex + 1,
                    count
                )
            }
        }
    }
    // ────────────────── 搜索结束 ────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        viewModel.setEditTextSavedState(binding.textEdit.onSaveInstanceState())
    }

    // ────────────── 选项菜单 ──────────────
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menuBinding = MenuBinding.inflate(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        updateSaveMenuItem()
        updateEncodingMenuItems()
        updateSearchMenuItem()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_save -> {
                save()
                true
            }
            R.id.action_search -> {
                if (searchBarVisible) {
                closeSearch()
                } else {
                openSearch()
                }
                true
            }
            R.id.action_reload -> {
                onReload()
                true
            }
            Menu.FIRST -> {
                viewModel.encoding.value = Charset.forName(item.titleCondensed!!.toString())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun updateSearchMenuItem() {
        if (!this::menuBinding.isInitialized) return
        // 文本尚未加载完成时禁用搜索
        menuBinding.searchItem.isEnabled =
            viewModel.textState.value is DataState.Success
    }

    fun onSupportNavigateUp(): Boolean {
        if (onBackPressedCallback.isEnabled) {
            onBackPressedCallback.handleOnBackPressed()
            return true
        }
        return false
    }

    override fun finish() {
        requireActivity().finish()
    }

    private fun onEncodingChanged(encoding: Charset) {
        updateEncodingMenuItems()
    }

    private fun updateEncodingMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val charsetName = viewModel.encoding.value.name()
        val charsetItem = menuBinding.encodingSubMenu.children
            .find { it.titleCondensed == charsetName }!!
        charsetItem.isChecked = true
    }

    private fun onTextStateChanged(state: DataState<String>) {
        updateTitle()
        updateSearchMenuItem()
        // 加载新文件时关闭搜索
        if (searchBarVisible) closeSearch()
        when (state) {
            is DataState.Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeOutUnsafe()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeInUnsafe()
                if (!viewModel.isTextChanged.value) {
                    setText(state.data)
                }
            }
            is DataState.Error -> {
                state.throwable.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = state.throwable.toString()
                binding.textEdit.fadeOutUnsafe()
            }
        }
    }

    private fun setText(text: String?) {
        isSettingText = true
        binding.textEdit.setText(text)
        isSettingText = false
        viewModel.isTextChanged.value = false
        // 新文本 → 清空搜索高亮
        clearSearchHighlights()
        searchHelper.clear()
        updateSearchMatchCount()
    }

    private fun onIsTextChangedChanged(changed: Boolean) {
        updateTitle()
    }

    private fun updateTitle() {
        val fileName = viewModel.file.value.fileName.toString()
        val changed = viewModel.isTextChanged.value
        requireActivity().title = getString(
            if (changed) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            }, fileName
        )
    }

    private fun onReload() {
        if (viewModel.isTextChanged.value) {
            ConfirmReloadDialogFragment.show(this)
        } else {
            reload()
        }
    }

    override fun reload() {
        viewModel.isTextChanged.value = false
        viewModel.reload()
    }

    private fun save() {
        val text = binding.textEdit.text.toString()
        viewModel.writeFile(argsFile, text, requireContext())
    }

    private fun onWriteFileStateChanged(state: ActionState<Pair<Path, String>, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> updateSaveMenuItem()
            is ActionState.Success -> {
                showToast(R.string.text_editor_save_success)
                viewModel.finishWritingFile()
                viewModel.isTextChanged.value = false
            }
            // The error will be toasted by service so we should never show it in UI.
            is ActionState.Error -> viewModel.finishWritingFile()
        }
    }

    private fun updateSaveMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.saveItem.isEnabled = viewModel.writeFileState.value.isReady
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class MenuBinding private constructor(
        val menu: Menu,
        val saveItem: MenuItem,
        val searchItem: MenuItem,
        val encodingSubMenu: SubMenu
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.text_editor, menu)
                val encodingSubMenu = menu.findItem(R.id.action_encoding).subMenu!!
                for ((charsetName, charset) in Charset.availableCharsets()) {
                    // HACK: Use titleCondensed to store charset name.
                    encodingSubMenu.add(Menu.NONE, Menu.FIRST, Menu.NONE, charset.displayName())
                        .titleCondensed = charsetName
                }
                encodingSubMenu.setGroupCheckable(Menu.NONE, true, true)
                return MenuBinding(
                    menu,
                    menu.findItem(R.id.action_save),
                    menu.findItem(R.id.action_search),
                    encodingSubMenu
                )
            }
        }
    }
}