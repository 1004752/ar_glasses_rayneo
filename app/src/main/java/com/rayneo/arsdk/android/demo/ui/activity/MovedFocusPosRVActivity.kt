package com.rayneo.arsdk.android.demo.ui.activity

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rayneo.arsdk.android.core.ViewPair
import com.rayneo.arsdk.android.demo.databinding.LayoutRecyclerviewMovedFocusBinding
import com.rayneo.arsdk.android.demo.ui.adapter.MovedFocusPosAdapter
import com.rayneo.arsdk.android.demo.ui.entity.contactList
import com.rayneo.arsdk.android.ui.toast.FToast
import com.rayneo.arsdk.android.ui.util.RecyclerViewFocusTracker
import com.rayneo.arsdk.android.touch.TempleAction
import com.rayneo.arsdk.android.touch.TempleActionViewModel
import com.rayneo.arsdk.android.ui.activity.BaseMirrorActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

/**
 * RecyclerView with fixed focus position
 */
class MovedFocusPosRVActivity : BaseMirrorActivity<LayoutRecyclerviewMovedFocusBinding>() {
    private lateinit var favoriteTracker: RecyclerViewFocusTracker
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favoriteTracker = RecyclerViewFocusTracker(
            ViewPair(mBindingPair.left.recyclerView, mBindingPair.right.recyclerView),
            ignoreDelta = 70
        )
        initView()
        initEvent()
        favoriteTracker.focusObj.hasFocus = true
    }

    private fun initEvent() {
        // 监听原始事件，实现跟手效果

        lifecycleScope.launchWhenResumed {
            val templeActionViewModel =
                ViewModelProvider(this@MovedFocusPosRVActivity).get<TempleActionViewModel>()
            templeActionViewModel.state.collectLatest {
                if (!favoriteTracker.focusObj.hasFocus || !this.isActive || it.consumed) {
                    return@collectLatest
                }
                favoriteTracker.handleActionEvent(it) { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> {
                            finish()
                        }
                        is TempleAction.Click -> {
                            if (!action.consumed) {
                                (mBindingPair.left.recyclerView.adapter as MovedFocusPosAdapter)
                                    .getCurrentData()?.apply {
                                        FToast.show(displayName)
                                    }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun initView() {
        val mPair = mBindingPair
        mPair.updateView {
            val isLeft = mPair.checkIsLeft(this)
            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = MovedFocusPosAdapter(context, isLeft, favoriteTracker).apply {
                    setData(contactList())
                }
                itemAnimator = null
            }
            favoriteTracker.setCurrentSelectPos(0)
        }
    }
}
