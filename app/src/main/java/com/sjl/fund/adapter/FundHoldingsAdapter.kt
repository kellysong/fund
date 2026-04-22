package com.sjl.fund.adapter

import android.graphics.Color
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.FundHolding

/**
 * 基金持仓列表适配器
 * @author Kelly
 * @version 1.0.0
 * @filename FundHoldingsAdapter
 * @time 2026/4/9
 */
class FundHoldingsAdapter : BaseQuickAdapter<FundHolding, BaseViewHolder>(R.layout.item_fund_holding) {

    override fun convert(holder: BaseViewHolder, item: FundHolding) {
        holder.setText(R.id.tv_stock_name, item.GPJC)
        holder.setText(R.id.tv_stock_code, item.GPDM)
        holder.setText(R.id.tv_ratio, item.JZBL)
        
        // 当日涨跌
        val dailyChangeText = holder.getView<android.widget.TextView>(R.id.tv_daily_change)
        dailyChangeText.text = item.dailyChange
        val dailyChangeValue = item.dailyChange.replace("%", "").toFloatOrNull() ?: 0f
        dailyChangeText.setTextColor(
            when {
                dailyChangeValue > 0 -> Color.parseColor("#FF4D4F")  // 红色
                dailyChangeValue < 0 -> Color.parseColor("#52C41A")  // 绿色
                else -> Color.parseColor("#999999")                  // 灰色
            }
        )
        
        // 较上期变动
        val changeText = holder.getView<android.widget.TextView>(R.id.tv_change)
        changeText.text = item.PCTNVCHG
        
        // 根据变动设置颜色
        val changeStr = item.PCTNVCHG.trim()
        when {
            changeStr.startsWith("+") || changeStr.contains("增") -> {
                changeText.setTextColor(Color.parseColor("#FF4D4F"))  // 红色
            }
            changeStr.startsWith("-") || changeStr.contains("减") -> {
                changeText.setTextColor(Color.parseColor("#52C41A"))  // 绿色
            }
            else -> {
                changeText.setTextColor(Color.parseColor("#999999"))  // 灰色
            }
        }
    }
}
