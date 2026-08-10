package com.sjl.fund.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sjl.fund.R
import com.sjl.fund.entity.UsIndexData
import kotlinx.android.synthetic.main.item_us_index_card.view.tv_us_index_name
import kotlinx.android.synthetic.main.item_us_index_card.view.tv_us_index_price
import kotlinx.android.synthetic.main.item_us_index_card.view.tv_us_index_change

class UsIndexAdapter : RecyclerView.Adapter<UsIndexAdapter.VH>() {

    private val list = mutableListOf<UsIndexData>()

    fun setNewInstance(data: List<UsIndexData>) {
        list.clear()
        list.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_us_index_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.itemView.tv_us_index_name.text = item.name
        // 点位保留两位小数
        val price = item.price.toDoubleOrNull()?.let { String.format("%.2f", it) } ?: item.price
        holder.itemView.tv_us_index_price.text = price
        // 涨跌值 + 涨跌幅，均保留两位小数
        val change = item.change.toDoubleOrNull() ?: 0.0
        val percent = item.changePercent.toDoubleOrNull() ?: 0.0
        val up = percent >= 0
        val chgValue = String.format("%+.2f", change)
        val chgPercent = String.format("%+.2f%%", percent)
        holder.itemView.tv_us_index_change.text = "$chgValue  $chgPercent"
        holder.itemView.tv_us_index_change.setTextColor(
            if (up) Color.parseColor("#E54545") else Color.parseColor("#2EAC69")
        )
    }

    override fun getItemCount(): Int = list.size

    class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView)
}
