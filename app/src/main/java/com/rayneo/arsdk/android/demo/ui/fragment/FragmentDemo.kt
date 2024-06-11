package com.rayneo.arsdk.android.demo.ui.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get
import androidx.lifecycle.lifecycleScope
import com.rayneo.arsdk.android.core.BaseScreenHolder
import com.rayneo.arsdk.android.demo.R
import com.rayneo.arsdk.android.demo.databinding.FragmentDemoBinding
import com.rayneo.arsdk.android.ui.toast.FToast
import com.rayneo.arsdk.android.focus.IFocusable
import com.rayneo.arsdk.android.focus.releaseFocus
import com.rayneo.arsdk.android.touch.TempleAction
import com.rayneo.arsdk.android.touch.TempleActionViewModel
import com.rayneo.arsdk.android.ui.fragment.BaseMirrorFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

class FragmentDemo :
    BaseMirrorFragment<FragmentDemoBinding, BaseScreenHolder<FragmentDemoBinding>>(), IFocusable {
    override var hasFocus: Boolean = false
        set(value) {
            field = value
            updateViewBkg()
        }


    override var focusParent: IFocusable? = null

    override fun onCreateView(rootView: View, savedInstanceState: Bundle?) {
        val content = arguments?.getString("content") ?: ""
        mBindingPair.updateView {
            tvContent.text = content
        }
        val templeActionViewModel =
            ViewModelProvider(requireActivity()).get<TempleActionViewModel>()
        lifecycleScope.launchWhenResumed {
            templeActionViewModel.state.collectLatest { action ->
                if (!hasFocus || !isActive || action.consumed) {
                    return@collectLatest
                }
                when (action) {
                    is TempleAction.DoubleClick -> {
                        // consumed the event
                        action.consumed = true
                        releaseFocus()
                    }
                    is TempleAction.Click -> {
                        FToast.show(mBindingPair.left.tvContent.text.toString())
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun updateViewBkg() {
        mBindingPair.updateView {
            if (hasFocus) {
                tvContent.setBackgroundColor(tvContent.context.getColor(R.color.teal_200))
            } else {
                tvContent.setBackgroundColor(tvContent.context.getColor(R.color.teal_700))
            }
        }
    }

    companion object {
        fun newInstance(content: String): FragmentDemo {
            val fragment = FragmentDemo()
            fragment.arguments = Bundle().apply {
                putString("content", content)
            }
            return fragment
        }
    }
}
