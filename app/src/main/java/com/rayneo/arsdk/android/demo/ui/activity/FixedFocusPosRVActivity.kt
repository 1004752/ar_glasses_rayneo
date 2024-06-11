package com.rayneo.arsdk.android.demo.ui.activity

import android.os.Bundle
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rayneo.arsdk.android.R
import com.rayneo.arsdk.android.core.ViewPair
import com.rayneo.arsdk.android.core.make3DEffectForSide
import com.rayneo.arsdk.android.demo.databinding.LayoutRecyclerviewBinding
import com.rayneo.arsdk.android.demo.ui.adapter.FixedFocusPosAdapter
import com.rayneo.arsdk.android.demo.ui.entity.contactList
import com.rayneo.arsdk.android.ui.toast.FToast
import com.rayneo.arsdk.android.ui.util.RecyclerViewSlidingTracker
import com.rayneo.arsdk.android.ext.dp
import com.rayneo.arsdk.android.ext.setViewVisible
import com.rayneo.arsdk.android.touch.TempleAction
import com.rayneo.arsdk.android.touch.TempleActionViewModel
import com.rayneo.arsdk.android.ui.activity.BaseMirrorActivity
import com.rayneo.arsdk.android.ui.activity.actionName
import com.rayneo.arsdk.android.util.FLogger
import com.rayneo.arsdk.android.util.StartSnapHelper
import kotlinx.coroutines.isActive

/**
 * RecyclerView with fixed focus position
 */
class FixedFocusPosRVActivity : BaseMirrorActivity<LayoutRecyclerviewBinding>() {
    private lateinit var favoriteTracker: RecyclerViewSlidingTracker
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favoriteTracker = RecyclerViewSlidingTracker(
            ViewPair(mBindingPair.left.recyclerView, mBindingPair.right.recyclerView)
        )
        initView()
        initEvent()
        favoriteTracker.focusObj.hasFocus = true
    }

    private fun initEvent() {
        // 监听原始事件，实现跟手效果
        favoriteTracker.observeOriginMotionEventStream(
            motionEventDispatcher
        ) { event ->
            MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                event.action,
                320f,
                event.x,
                event.metaState
            ).apply {
                val e = this
                FLogger.d(
                    "onReceiveEvent：x = ${e.x}, y = ${e.y},action = ${e.actionName()}, deviceId = ${e.deviceId}"
                )
            }
        }

        lifecycleScope.launchWhenResumed {
            val templeActionViewModel =
                ViewModelProvider(this@FixedFocusPosRVActivity).get<TempleActionViewModel>()
            templeActionViewModel.state.collect {
                if (!favoriteTracker.focusObj.hasFocus || !this.isActive || it.consumed) {
                    return@collect
                }
                favoriteTracker.handleActionEvent(it) { action ->
                    when (action) {
                        is TempleAction.DoubleClick -> {
                            finish()
                        }
                        is TempleAction.Click -> {
                            if (!action.consumed) {
                                (mBindingPair.left.recyclerView.adapter as FixedFocusPosAdapter)
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
                adapter = FixedFocusPosAdapter(context, isLeft, favoriteTracker).apply {
                    setData(contactList(true))
                }
                itemAnimator = null
                val snapHelper = StartSnapHelper(41.dp)
                snapHelper.attachToRecyclerView(this)
                setTag(R.id.tag_snap_helper, snapHelper)
            }
            make3DEffectForSide(this.ivSelectHover, isLeft, isLeft)
            favoriteTracker.setCurrentSelectPos(0)
            setViewVisible(true, ivSelectHover)
        }
    }
}
