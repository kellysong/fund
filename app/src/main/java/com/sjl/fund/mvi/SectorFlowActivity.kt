package com.sjl.fund.mvi

import android.graphics.Color
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.tabs.TabLayout
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.fund.R
import com.sjl.fund.adapter.SectorFlowAdapter
import com.sjl.fund.entity.SectorFlowData
import kotlinx.android.synthetic.main.sector_flow_activity.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 板块主力资金流向二级页面（行业 / 概念 / 风格 / 地域）
 * 采用 MVI 模式，参考 FundListActivity2
 * @author Kelly
 * @version 1.0.0
 */
class SectorFlowActivity : BaseViewModelActivity<SectorFlowViewModel>() {

    override fun getLayoutId(): Int = R.layout.sector_flow_activity

    override fun providerVMClass(): Class<SectorFlowViewModel>? = SectorFlowViewModel::class.java

    private var sectorAdapter: SectorFlowAdapter? = null

    override fun initView() {
        // 返回
        iv_back.setOnClickListener { finish() }
        // 刷新
        iv_refresh.setOnClickListener {
            viewModel.dispatch(SectorFlowIntent.RefreshData)
        }

        // 列表
        rv_sector.layoutManager = LinearLayoutManager(this)
        sectorAdapter = SectorFlowAdapter()
        rv_sector.adapter = sectorAdapter

        // 图表初始配置
        setupChart()
    }

    override fun initListener() {
        tl_board.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val board = tab?.text?.toString() ?: "行业"
                viewModel.dispatch(SectorFlowIntent.SwitchBoard(board))
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun initData() {
        // 默认加载第一个 Tab：行业
        viewModel.dispatch(SectorFlowIntent.LoadSectorData("行业"))
    }

    /**
     * 配置主力资金流向柱状图
     */
    private fun setupChart() {
        chart.setDrawBarShadow(false)
        chart.setDrawValueAboveBar(true)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setPinchZoom(false)
        chart.setScaleEnabled(false)
        chart.setFitBars(true)
        chart.isDoubleTapToZoomEnabled = false

        // X 轴：板块名称（在左侧）
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.granularity = 1f
        xAxis.textSize = 12f
        xAxis.textColor = Color.parseColor("#333333")
        xAxis.labelCount = 15

        // 隐藏左侧数值轴
        chart.axisLeft.isEnabled = true
        chart.axisLeft.setDrawGridLines(false)
        chart.axisLeft.setDrawAxisLine(false)
        chart.axisLeft.textSize = 9f
        chart.axisLeft.textColor = Color.parseColor("#999999")

        // 右侧数值轴：显示单位
        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false
    }

    /**
     * 更新主力净流入柱状图（取 Top15，净流入最大在最上方）
     */
    private fun updateChart(list: List<SectorFlowData>) {
        chart.clear()
        chart.fitScreen()
        val top = list.take(15).reversed() // 反转使最大在最上方
        if (top.isEmpty()) return

        val entries = ArrayList<BarEntry>()
        val colors = ArrayList<Int>()
        val redColor = Color.parseColor("#E54545")
        val greenColor = Color.parseColor("#2EAC69")
        var maxAbs = 0f

        for ((i, item) in top.withIndex()) {
            val yi = (item.mainNetInflow / 1e8).toFloat()
            entries.add(BarEntry(i.toFloat(), yi))
            colors.add(if (yi >= 0) redColor else greenColor)
            maxAbs = maxOf(maxAbs, kotlin.math.abs(yi))
        }

        val dataSet = BarDataSet(entries, "")
        dataSet.colors = colors
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.parseColor("#333333")
        dataSet.setDrawValues(true)
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return String.format("%.2f亿", value)
            }
        }

        val data = BarData(dataSet)
        data.barWidth = 0.7f
        chart.data = data

        // 显式设 Y 轴范围，防止数值标签被裁掉
        val margin = maxAbs * 0.25f
        val minVal = entries.minOf { it.y }
        chart.axisLeft.axisMinimum = (if (minVal < 0) minVal - margin else -margin)
        chart.axisLeft.axisMaximum = maxAbs + margin

        // X 轴显示板块名称，长名称只取前6字
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                if (idx in top.indices) {
                    val n = top[idx].name
                    return if (n.length > 6) n.substring(0, 6) + ".." else n
                }
                return ""
            }
        }
        chart.xAxis.axisMinimum = -0.5f
        chart.xAxis.axisMaximum = top.size - 0.5f

        chart.notifyDataSetChanged()
        chart.invalidate()
        chart.post { chart.animateY(300) }
    }

    override fun startObserve() {
        lifecycleScope.launch {
            viewModel.viewState.collect { state ->
                when (state) {
                    is SectorFlowUiState.LoadSuccess -> {
                        sectorAdapter?.setNewInstance(state.data.toMutableList())
                        updateChart(state.data)
                    }
                    is SectorFlowUiState.LoadError -> {
                        Toast.makeText(
                            this@SectorFlowActivity,
                            "加载失败：${state.error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}
