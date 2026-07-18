package com.sjl.fund.adapter

import android.graphics.Color
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.FundBondHolding

/**
 * 重仓债券列表适配器
 */
class FundBondHoldingsAdapter :
    BaseQuickAdapter<FundBondHolding, BaseViewHolder>(R.layout.item_bond_holding) {

    override fun convert(holder: BaseViewHolder, item: FundBondHolding) {
        holder.setText(R.id.tv_bond_name, item.ZQMC)
        holder.setText(R.id.tv_bond_code, item.ZQDM)
        holder.setText(R.id.tv_bond_ratio, item.JZBL)
        holder.setText(R.id.tv_bond_change, item.PCTNVCHG)
        // 较上期：涨(增)红、跌(减)绿、新进/--灰
        val changeColor = when {
            item.PCTNVCHG.startsWith("+") -> Color.parseColor("#FFE54545")
            item.PCTNVCHG.startsWith("-") -> Color.parseColor("#FF2EAC69")
            else -> Color.parseColor("#999999")
        }
        holder.setTextColor(R.id.tv_bond_change, changeColor)
    }
}
