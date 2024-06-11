package com.rayneo.arsdk.android.demo.ui.wedget

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rayneo.arsdk.android.core.make3DEffectForSide
import com.rayneo.arsdk.android.demo.R
import com.rayneo.arsdk.android.demo.databinding.WidgetTitleLayoutBinding
import com.rayneo.arsdk.android.ui.util.FixPosFocusTracker
import com.rayneo.arsdk.android.ui.util.FocusHolder
import com.rayneo.arsdk.android.ui.util.FocusInfo
import com.rayneo.arsdk.android.ui.wiget.BaseMirrorContainerView
import com.rayneo.arsdk.android.focus.IFocusable
import com.rayneo.arsdk.android.touch.TempleAction
import com.rayneo.arsdk.android.touch.TempleActionViewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive

class TitleView : BaseMirrorContainerView<WidgetTitleLayoutBinding>, IFocusable {
    private var selectPos = 1
    private lateinit var titles: Array<String>

    override var hasFocus: Boolean = true
        set(value) {
            field = value
            focusTracker.apply {
                focusObj.hasFocus = field
                val current = focusHolder.currentFocusItem
                focusHolder.currentFocus(current.target)
            }
        }

    override var focusParent: IFocusable? = null
    lateinit var focusTracker: FixPosFocusTracker

    var onTitleSelectListener: OnTitleSelectListener? = null


    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr)


    override fun onInit() {
        val focusHolder = FocusHolder(false)
        mBindingPair.setLeft {
            val btn1Info = FocusInfo(
                tvTitle1,
                eventHandler = { action ->
                    when (action) {
                        is TempleAction.Click -> {
                            onTitleSelectListener?.onTitleSelect(0, tvTitle1)
                        }
                        else -> Unit
                    }
                },
                focusChangeHandler = { hasFocus ->
                    mBindingPair.updateView {
                        triggerFocus(hasFocus, tvTitle1, mBindingPair.checkIsLeft(this))
                    }
                }
            )
            focusHolder.addFocusTarget(
                btn1Info,
                FocusInfo(
                    tvTitle2,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                onTitleSelectListener?.onTitleSelect(1, tvTitle2)
                            }
                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, tvTitle2, mBindingPair.checkIsLeft(this))
                        }
                    }
                ),
                FocusInfo(
                    tvTitle3,
                    eventHandler = { action ->
                        when (action) {
                            is TempleAction.Click -> {
                                onTitleSelectListener?.onTitleSelect(2, tvTitle3)
                            }
                            else -> Unit
                        }
                    },
                    focusChangeHandler = { hasFocus ->
                        mBindingPair.updateView {
                            triggerFocus(hasFocus, tvTitle3, mBindingPair.checkIsLeft(this))
                        }
                    }
                )
            )
        }

        focusTracker = FixPosFocusTracker(focusHolder, true).apply {
            focusObj.hasFocus = hasFocus
        }
        focusHolder.currentFocus(mBindingPair.left.tvTitle1)
    }

    private fun triggerFocus(focus: Boolean, view: TextView, isLeft: Boolean) {
        if (focus) {
            if (hasFocus) {
                make3DEffectForSide(view, isLeft, hasFocus)
                view.setBackgroundResource(R.drawable.shape_device_selected)
            } else {
                view.setBackgroundResource(R.drawable.shape_phone_top_tip)
            }
        } else {
            view.setBackgroundColor(context.getColor(R.color.black))
        }
    }

    fun setTitles(titles: Array<String>) {
        this.titles = titles

        mBindingPair.updateView {
            tvTitle1.text = titles[0]
            tvTitle2.text = titles[1]
            tvTitle3.text = titles[2]
        }
    }

    fun watchAction(
        act: AppCompatActivity,
        templeActionViewModel: TempleActionViewModel,
    ) {
        val lifecycleScope = act.lifecycleScope
        lifecycleScope.launchWhenResumed {
            templeActionViewModel.state.filter { !it.consumed }.collect { action ->
                if (!hasFocus || !this.isActive) {
                    return@collect
                }
                when (action) {
                    is TempleAction.DoubleClick -> {
                        act.finish()
                    }
                    else -> focusTracker.handleFocusTargetEvent(action)
                }
            }
        }
    }
}

interface OnTitleSelectListener {
    fun onTitleSelect(pos: Int, titleView: TextView)
}
