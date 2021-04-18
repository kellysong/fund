package com.sjl.fund.adapter

import android.graphics.Color
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.module.DraggableModule
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.sjl.fund.R
import com.sjl.fund.entity.FundInfo

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
    override fun convert(baseViewHolder: BaseViewHolder, fundInfo: FundInfo) {
        with(baseViewHolder) {
            setText(R.id.tv_name, fundInfo.name)
                    .setText(R.id.tv_code,fundInfo.fundcode)
                    .setText(R.id.tv_code,fundInfo.fundcode)
                    .setText(R.id.tv_jz_date,fundInfo.jzrq)
                    .setText(R.id.tv_jz_v1,fundInfo.dwjz)

                    .setText(R.id.tv_gz_date,fundInfo.gztime)
                    .setText(R.id.tv_gz_v1,fundInfo.gsz)
                    .setText(R.id.tv_gz_v2,fundInfo.gszzl)
        }
        if (fundInfo.gszzl.toDouble() > 0){
            baseViewHolder.setTextColor(R.id.tv_gz_v2,Color.RED)
        }else{
            baseViewHolder.setTextColor(R.id.tv_gz_v2,Color.GREEN)

        }
    }
}