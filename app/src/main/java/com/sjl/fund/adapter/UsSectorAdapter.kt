package com.sjl.fund.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sjl.fund.R
import com.sjl.fund.entity.UsSectorData
import kotlinx.android.synthetic.main.item_us_sector.view.tv_sector_chg
import kotlinx.android.synthetic.main.item_us_sector.view.tv_sector_lead
import kotlinx.android.synthetic.main.item_us_sector.view.tv_sector_lead_chg
import kotlinx.android.synthetic.main.item_us_sector.view.tv_sector_name

class UsSectorAdapter : RecyclerView.Adapter<UsSectorAdapter.VH>() {

    private val list = mutableListOf<UsSectorData>()

    fun setNewInstance(data: List<UsSectorData>) {
        list.clear()
        list.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_us_sector, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.itemView.tv_sector_name.text = item.name
        holder.itemView.tv_sector_lead.text = item.leader.ifEmpty { "--" }
        val chg = String.format("%+.2f%%", item.changePercent)
        holder.itemView.tv_sector_chg.text = chg
        holder.itemView.tv_sector_chg.setTextColor(
            if (item.changePercent >= 0) Color.parseColor("#E54545")
            else Color.parseColor("#2EAC69")
        )
        val leadChg = String.format("%+.2f%%", item.leaderChangePercent)
        holder.itemView.tv_sector_lead_chg.text = leadChg
        holder.itemView.tv_sector_lead_chg.setTextColor(
            if (item.leaderChangePercent >= 0) Color.parseColor("#E54545")
            else Color.parseColor("#2EAC69")
        )
    }

    override fun getItemCount(): Int = list.size

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView)
}
