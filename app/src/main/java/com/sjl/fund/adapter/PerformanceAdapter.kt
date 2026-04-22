package com.sjl.fund.adapter

import android.graphics.Color
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.FundPerformance

/**
 * 历史业绩列表适配器
 * @author Kelly
 * @version 1.0.0
 * @filename PerformanceAdapter
 * @time 2026/4/9
 */
class PerformanceAdapter : BaseQuickAdapter<FundPerformance, BaseViewHolder>(R.layout.item_performance) {

    override fun convert(holder: BaseViewHolder, item: FundPerformance) {
        holder.setText(R.id.tv_period, item.period)
        holder.setText(R.id.tv_rank, item.rank)

        val returnText = holder.getView<android.widget.TextView>(R.id.tv_fund_return)
        returnText.text = item.fundReturn

        if (item.fundReturn == "--") {
            returnText.setTextColor(Color.parseColor("#999999"))
            return
        }

        val value = item.fundReturn.replace("%", "").replace("+", "").toFloatOrNull() ?: 0f
        returnText.setTextColor(
            when {
                value > 0 -> Color.parseColor("#E54545")  // 红色-涨
                value < 0 -> Color.parseColor("#2EAC69")  // 绿色-跌
                else -> Color.parseColor("#999999")
            }
        )
    }
}
