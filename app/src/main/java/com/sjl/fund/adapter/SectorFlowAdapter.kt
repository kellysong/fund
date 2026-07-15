package com.sjl.fund.adapter

import android.graphics.Color
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.SectorFlowData

/**
 * 板块主力资金流向列表适配器
 * @author Kelly
 * @version 1.0.0
 */
class SectorFlowAdapter :
    BaseQuickAdapter<SectorFlowData, BaseViewHolder>(R.layout.item_sector_flow) {

    override fun convert(holder: BaseViewHolder, item: SectorFlowData) {
        holder.setText(R.id.tv_sector_name, item.name)
        holder.setText(R.id.tv_sector_code, item.code)

        // 涨跌幅
        val pct = item.changePercent
        val pctText = if (pct >= 0) String.format("+%.2f%%", pct) else String.format("%.2f%%", pct)
        val tvPct = holder.getView<android.widget.TextView>(R.id.tv_sector_change)
        tvPct.text = pctText
        tvPct.setTextColor(if (pct >= 0) Color.parseColor("#E54545") else Color.parseColor("#2EAC69"))

        // 主力净流入
        val mainYi = item.mainNetInflow / 1e8
        val tvMain = holder.getView<android.widget.TextView>(R.id.tv_main_inflow)
        tvMain.text = formatYi(mainYi)
        tvMain.setTextColor(if (mainYi >= 0) Color.parseColor("#E54545") else Color.parseColor("#2EAC69"))

        // 主力净占比
        val pctInflow = item.mainNetInflowPercent
        holder.setText(
            R.id.tv_main_percent,
            (if (pctInflow >= 0) "+" else "") + String.format("%.2f%%", pctInflow)
        )

        // 超大单 / 大单 / 中单 / 小单 净流入
        holder.setText(R.id.tv_extra_large, formatYi(item.extraLargeNetInflow / 1e8))
        holder.setText(R.id.tv_large, formatYi(item.largeNetInflow / 1e8))
        holder.setText(R.id.tv_medium, formatYi(item.mediumNetInflow / 1e8))
        holder.setText(R.id.tv_small, formatYi(item.smallNetInflow / 1e8))
    }

    private fun formatYi(yi: Double): String {
        return if (yi >= 0) String.format("+%.2f亿", yi) else String.format("%.2f亿", yi)
    }
}
