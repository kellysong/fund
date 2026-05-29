package com.sjl.fund.mvvm

import android.graphics.Color
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.tabs.TabLayout
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.R
import com.sjl.fund.adapter.FundHoldingsAdapter
import com.sjl.fund.adapter.NetValueHistoryAdapter
import com.sjl.fund.adapter.PerformanceAdapter
import com.sjl.fund.entity.FundHolding
import com.sjl.fund.entity.FundHistoryNetValue
import com.sjl.fund.entity.FundTrendPoint
import kotlinx.android.synthetic.main.fund_detail_activity.*

/**
 * 基金详情页面 - 支付宝风格
 * 支持：业绩走势（百分比/7时间段）、历史净值/历史业绩Tab、基金持仓、资产分布饼图
 * @author Kelly
 * @version 4.0.0
 * @filename FundDetailActivity
 * @time 2026/4/9
 */
class FundDetailActivity : BaseViewModelActivity<FundDetailViewModel>() {

    companion object {
        const val EXTRA_FUND_CODE = "fund_code"
        const val EXTRA_FUND_NAME = "fund_name"
    }

    private lateinit var fundCode: String
    private lateinit var fundName: String

    private var holdingsAdapter: FundHoldingsAdapter? = null
    private var historyAdapter: NetValueHistoryAdapter? = null
    private var performanceAdapter: PerformanceAdapter? = null

    // 当前选中时间段key
    private var currentPeriodKey = "1m"
    // 全量走势数据
    private var allTrendData: List<FundTrendPoint> = emptyList()

    override fun getLayoutId(): Int = R.layout.fund_detail_activity

    override fun providerVMClass(): Class<FundDetailViewModel>? = FundDetailViewModel::class.java

    override fun initView() {
        fundCode = intent.getStringExtra(EXTRA_FUND_CODE) ?: ""
        fundName = intent.getStringExtra(EXTRA_FUND_NAME) ?: ""

        if (fundCode.isEmpty()) {
            finish()
            return
        }

        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
        }

        tv_fund_name.text = fundName
        tv_fund_code.text = fundCode

        initLineChart()
        initBarChart()

        holdingsAdapter = FundHoldingsAdapter()
        rv_holdings.layoutManager = LinearLayoutManager(this)
        rv_holdings.adapter = holdingsAdapter
        rv_holdings.isNestedScrollingEnabled = false

        historyAdapter = NetValueHistoryAdapter()
        rv_net_value_history.layoutManager = LinearLayoutManager(this)
        rv_net_value_history.adapter = historyAdapter
        rv_net_value_history.isNestedScrollingEnabled = false

        performanceAdapter = PerformanceAdapter()
        rv_performance.layoutManager = LinearLayoutManager(this)
        rv_performance.adapter = performanceAdapter
        rv_performance.isNestedScrollingEnabled = false

        initHistoryTab()
    }

    private fun initHistoryTab() {
        tl_history.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        ll_net_value.visibility = View.VISIBLE
                        ll_performance.visibility = View.GONE
                    }
                    1 -> {
                        ll_net_value.visibility = View.GONE
                        ll_performance.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    override fun initListener() {
        rg_time_range.setOnCheckedChangeListener { _, checkedId ->
            currentPeriodKey = when (checkedId) {
                R.id.rb_1m -> "1m"
                R.id.rb_3m -> "3m"
                R.id.rb_6m -> "6m"
                R.id.rb_1y -> "1y"
                R.id.rb_3y -> "3y"
                R.id.rb_5y -> "5y"
                R.id.rb_ls -> "ls"
                else -> "1m"
            }
            updateChart()
        }
    }

    override fun initData() {
        viewModel.loadFundDetail(fundCode)
    }

    override fun startObserve() {
        viewModel.loading.observe(this, Observer { isLoading ->
            progress_bar.visibility = if (isLoading) View.VISIBLE else View.GONE
        })

        viewModel.error.observe(this, Observer { error ->
            LogUtils.e("加载失败", error)
        })

        viewModel.netValueInfo.observe(this, Observer { (dwjz, ljjz, date) ->
            tv_net_value.text = dwjz
            tv_cumulative_value.text = ljjz
            tv_value_date.text = date
        })

        viewModel.historyNetValues.observe(this, Observer { list ->
            historyAdapter?.setNewInstance(list.toMutableList())
        })

        viewModel.allTrendPoints.observe(this, Observer { list ->
            allTrendData = list
            updateChart()
        })

        viewModel.performances.observe(this, Observer { list ->
            performanceAdapter?.setNewInstance(list.toMutableList())
        })

        viewModel.holdings.observe(this, Observer { list ->
            holdingsAdapter?.setNewInstance(list.toMutableList())
            tv_holdings_title.text = "基金持仓 (${list.size})"
            updateAssetAllocation(list)
        })

        viewModel.realTimeValue.observe(this, Observer { (gsz, gszzl, gztime) ->
            // 更新顶部收益显示
            val changeValue = gszzl.replace("%", "").toFloatOrNull() ?: 0f
            tv_change_percent.text = if (changeValue >= 0) "+$gszzl%" else "$gszzl%"
            val color = if (changeValue >= 0) Color.parseColor("#FFE54545") else Color.parseColor("#FF2EAC69")
            tv_change_percent.setTextColor(color)
        })
    }

    // ---- 折线图 ----

    /** 点击 Marker：显示日期 + 收益率 */
    private inner class TrendMarkerView : MarkerView(this@FundDetailActivity, R.layout.marker_trend) {
        private val tvDate: android.widget.TextView by lazy { findViewById(R.id.tv_marker_date) }
        private val tvValue: android.widget.TextView by lazy { findViewById(R.id.tv_marker_value) }

        override fun refreshContent(e: Entry?, highlight: Highlight?) {
            val idx = e?.x?.toInt() ?: return
            val dateList = (tag as? List<*>)?.filterIsInstance<String>() ?: return
            val date = if (idx in dateList.indices) dateList[idx] else ""
            val pct = e.y
            tvDate.text = date
            tvValue.text = String.format("%+.2f%%", pct)
            tvValue.setTextColor(
                if (pct >= 0) Color.parseColor("#E54545") else Color.parseColor("#2EAC69")
            )
            super.refreshContent(e, highlight)
        }

        override fun getOffset(): MPPointF {
            return MPPointF(-(width / 2f), -height.toFloat() - 10f)
        }
    }

    private fun initLineChart() {
        line_chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(false)
            setPinchZoom(false)
            isHighlightPerTapEnabled = true
            isHighlightPerDragEnabled = true

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                labelCount = 5
                textColor = Color.parseColor("#999999")
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#EEEEEE")
                textColor = Color.parseColor("#999999")
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format("%.2f%%", value)
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            // 减少左右间距，避免浪费空间
            setExtraOffsets(2f, 8f, 2f, 8f)
        }
    }

    /**
     * 根据当前时间段截取数据并刷新图表
     * 业绩走势图表使用蓝色，展示相对于区间起始日的累计收益率(%)
     */
    private fun updateChart() {
        if (allTrendData.isEmpty()) return

        val filtered = viewModel.getTrendByPeriod(currentPeriodKey, allTrendData)
        if (filtered.isEmpty()) return

        val baseValue = filtered.first().netValue
        val dateList = filtered.map { it.date }

        // X轴日期格式化
        line_chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in dateList.indices) dateList[idx].substring(5) else ""
            }
        }

        val entries = filtered.mapIndexed { idx, point ->
            val pct = if (baseValue != 0f) ((point.netValue - baseValue) / baseValue * 100f) else 0f
            Entry(idx.toFloat(), pct)
        }

        // 总是蓝色
        val lineColor = Color.parseColor("#1677FF")
        val fillColor = Color.parseColor("#1677FF")

        val dataSet = LineDataSet(entries, "收益率").apply {
            color = lineColor
            setDrawCircles(false)
            lineWidth = 1.5f
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            this.fillColor = fillColor
            fillAlpha = 30
            setDrawValues(false)
            // 点击高亮点样式
            highLightColor = Color.parseColor("#1677FF")
            highlightLineWidth = 1f
            enableDashedHighlightLine(4f, 4f, 0f)
        }

        // 设置 Marker，把日期列表通过 tag 传入
        val marker = TrendMarkerView().also { it.tag = dateList }
        line_chart.marker = marker

        line_chart.data = LineData(dataSet)
        line_chart.invalidate()
        line_chart.animateX(400)
    }

    // ---- 水平柱状图（资产分布） ----

    private fun initBarChart() {
        bar_chart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            setDrawValueAboveBar(true)
            legend.isEnabled = false

            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#999999")
                textSize = 10f
                granularity = 1f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#EEEEEE")
                textColor = Color.parseColor("#999999")
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format("%.1f%%", value)
                    }
                }
            }
            axisRight.isEnabled = false
            setExtraOffsets(4f, 8f, 4f, 8f)
        }
    }

    private fun updateAssetAllocation(holdings: List<FundHolding>) {
        if (holdings.isEmpty()) return

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        val colors = mutableListOf<Int>()

        // 取前10条
        holdings.take(10).forEachIndexed { index, holding ->
            val pct = holding.JZBL.replace("%", "").toFloatOrNull() ?: 0f
            if (pct > 0) {
                entries.add(BarEntry(index.toFloat(), pct))
                labels.add(holding.GPJC)
                colors.add(ColorTemplate.MATERIAL_COLORS[index % ColorTemplate.MATERIAL_COLORS.size])
            }
        }

        if (entries.isEmpty()) return

        // X轴标签
        bar_chart.xAxis.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    return if (idx in labels.indices) labels[idx] else ""
                }
            }
        }

        val dataSet = BarDataSet(entries, "").apply {
            this.colors = colors
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#333333")
            valueFormatter = object : ValueFormatter() {
                fun getBarFormattedValue(value: Float): String {
                    return String.format("%.2f%%", value)
                }
            }
        }

        bar_chart.data = BarData(dataSet).apply {
            barWidth = 0.6f
        }
        bar_chart.invalidate()
        bar_chart.animateY(400)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
