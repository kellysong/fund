package com.sjl.fund.adapter

import android.graphics.Color
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.IndexData

/**
 * 指数行情水平滚动适配器
 * @author Kelly
 * @version 1.0.0
 */
class IndexListAdapter : BaseQuickAdapter<IndexData, BaseViewHolder>(R.layout.item_index_card) {

    override fun convert(holder: BaseViewHolder, item: IndexData) {
        holder.setText(R.id.tv_index_name, item.name)
        holder.setText(R.id.tv_index_price, item.price)

        val changeTextView = holder.getView<android.widget.TextView>(R.id.tv_index_change)
        val changeText = "${item.change}  ${item.changePercent}%"
        changeTextView.text = changeText

        val changePct = item.changePercent.replace("+", "").toFloatOrNull() ?: 0f
        if (changePct > 0) {
            changeTextView.setTextColor(Color.parseColor("#E54545"))
        } else if (changePct < 0) {
            changeTextView.setTextColor(Color.parseColor("#2EAC69"))
        } else {
            changeTextView.setTextColor(Color.parseColor("#999999"))
        }
    }
}
