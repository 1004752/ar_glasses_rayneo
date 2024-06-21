package com.sk.csplayer.ui

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.rayneo.arsdk.android.demo.R

class ContentsListAdapter(context: Context) : BaseAdapter() {
    private val mContentList = ArrayList<ContentItem>()
    private val mInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private var mIsSelectEffect = false
    private val mTypeFace: Typeface =
        Typeface.createFromAsset(context.assets, "fonts/SKBRixHeadB.ttf")

    class ContentItem(var appId: Int, var thumbnailId: Int, var title: String)

    init {
        initContents()
    }

    fun setSelectEffect(value: Boolean) {
        mIsSelectEffect = value
    }

    private fun initContents() {
        mContentList.add(ContentItem(100, R.drawable.thumb_17, "VCS App 1"))
        mContentList.add(ContentItem(200, R.drawable.thumb_13, "VCS App 2"))
        mContentList.add(ContentItem(300, R.drawable.thumb_07, "VCS App 3"))
    }

    override fun getCount(): Int {
        return mContentList.size
    }

    override fun getItem(arg0: Int): Any {
        return mContentList[arg0]
    }

    override fun getItemId(arg0: Int): Long {
        return arg0.toLong()
    }

    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.content_list_item, null)
        }

        val item = getItem(position) as ContentItem

        val title = convertView.findViewById<View>(R.id.title) as TextView
        title.setTypeface(mTypeFace)

        val thumbnail = convertView.findViewById<View>(R.id.thumbnail) as LinearLayout
        if (mIsSelectEffect) {
            val effectView = convertView.findViewById<View>(R.id.effect_view) as View
            effectView.setBackgroundResource(R.drawable.selector_item)
        }

        title.text = item.title
        thumbnail.setBackgroundResource(item.thumbnailId)

        return convertView
    }
}