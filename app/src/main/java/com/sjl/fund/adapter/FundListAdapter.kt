package com.sjl.fund.adapter

import android.graphics.Color
import android.text.TextUtils
import android.view.ViewGroup
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.module.DraggableModule
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.util.MoneyUtils
import java.math.BigDecimal

/**
 * TODO
 *
 * @author Kelly
 * @version 1.0.0
 * @filename ArticleAdapter
 * @time 2021/1/7 12:02
 * @copyright(C) 2021 song
 */
class FundListAdapter(data: List<FundInfo>?) : BaseQuickAdapter<FundInfo, BaseViewHolder>(R.layout.fund_list_recycle_item, data as MutableList<FundInfo>?), DraggableModule {

    init {
        //首先会按顺序执行类中init代码块，然后再执行构造方法里代码，并且我可以在init代码块中使用类声明的属
        addChildClickViewIds(R.id.tv_name)
    }

    override fun convert(baseViewHolder: BaseViewHolder, fundInfo: FundInfo) {
        with(baseViewHolder) {

            setText(R.id.tv_name, fundInfo.name)
                    .setText(R.id.tv_code, fundInfo.fundcode)
                    .setText(R.id.tv_code, fundInfo.fundcode)
                    .setText(R.id.tv_jz_date, fundInfo.jzrq)
                    .setText(R.id.tv_jz_v1, fundInfo.dwjz)

                    .setText(R.id.tv_gz_date, fundInfo.gztime)
                    .setText(R.id.tv_gz_v1, fundInfo.gsz)
                    .setText(R.id.tv_gz_v2, fundInfo.gszzl)
                    .setGone(R.id.tv_hold, fundInfo.holdFlag == 0)
            if (fundInfo.holdFlag == 0) {
                setGone(R.id.tv_money_title, true).setGone(R.id.tv_hold_money, true)
            } else {
                setGone(R.id.tv_money_title, false).setGone(R.id.tv_hold_money, false)
                        .setText(R.id.tv_hold_money, MoneyUtils.formatMoney(fundInfo.holdMoney.toString(), 2))
            }
//                    .setGone(R.id.tv_hold_money,fundInfo.holdFlag == 0)
        }
        //https://github.com/CymChad/BaseRecyclerViewAdapterHelper/issues/3305#issuecomment-712553987,下面会导致列表第一项不会在onitemchildclick里面响应，
        // 需要在activity里 adapter.addChildClickViewIds或在adapter的构造addChildClickViewIds()添加点击事件
//        addChildClickViewIds(R.id.tv_name)
        if (!TextUtils.isEmpty(fundInfo.gszzl)&&fundInfo.gszzl.toDouble() > 0) {
            baseViewHolder.setTextColor(R.id.tv_gz_v2, Color.RED)
        } else {
            baseViewHolder.setTextColor(R.id.tv_gz_v2, Color.GREEN)

        }

    }

}