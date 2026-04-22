package com.sjl.fund.adapter

import android.graphics.Color
import android.text.TextUtils
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.FundHistoryNetValue

/**
 * 历史净值列表适配器
 * @author Kelly
 * @version 1.0.0
 */
class NetValueHistoryAdapter : BaseQuickAdapter<FundHistoryNetValue, BaseViewHolder>(R.layout.item_net_value_history) {

    override fun convert(holder: BaseViewHolder, item: FundHistoryNetValue) {
        // 日期
        holder.setText(R.id.tv_date, item.FSRQ)
        
        // 单位净值
        holder.setText(R.id.tv_net_value, item.DWJZ)
        
        // 累计净值
        holder.setText(R.id.tv_cumulative_value, item.LJJZ)

        // 涨跌幅 - 红涨绿跌
        val changeTextView = holder.getView<android.widget.TextView>(R.id.tv_change)
        val changeStr = item.JZZZL ?: ""
        
        // 解析涨跌幅数值
        val changeValue = changeStr.replace("%", "").replace("--", "0").trim().toFloatOrNull() ?: 0f
        
        // 格式化显示文本
        val displayText = when {
            TextUtils.isEmpty(changeStr) || changeStr == "--" -> "--"
            changeStr.startsWith("-") -> changeStr  // 跌，保持原样
            changeValue == 0f -> "0.00%"
            else -> "+$changeStr"  // 涨，添加+号
        }
        
        changeTextView.text = displayText
        
        // 设置颜色：红涨绿跌
        changeTextView.setTextColor(
            when {
                changeValue > 0 -> Color.parseColor("#E54545")   // 红色
                changeValue < 0 -> Color.parseColor("#2EAC69")   // 绿色
                else -> Color.parseColor("#999999")               // 灰色
            }
        )
    }
}