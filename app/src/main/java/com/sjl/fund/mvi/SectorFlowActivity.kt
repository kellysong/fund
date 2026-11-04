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
import com.sjl.fund.util.PageLoading
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

    // 页面加载 loading（统一封装，复用 PageLoading）
    private val pageLoading by lazy { PageLoading(this) }

    override fun initView() {
        // 返回
        iv_back.setOnClickListener { finish() }
        // 刷新（切换升降序）
        iv_refresh.setOnClickListener {
            viewModel.isAscending = !viewModel.isAscending
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
        // 默认加载第一个 Tab：行业（ready()/startObserve 先于 initData 执行，订阅已建立后才发起加载）
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
     * 主力资金流向柱状图（降序：取前15流入，升序：取后15流出）
     */
    private fun updateChart(list: List<SectorFlowData>) {
        chart.clear()
        chart.fitScreen()
        val top = if (!viewModel.isAscending) {
            list.take(15).reversed()       // 降序：流入Top15，反转使最大在上
        } else {
            list.reversed().take(15).reversed()        // 升序：反转后取前15 = 流出Top15
        }
        if (top.isEmpty()) return

        val entries = ArrayList<BarEntry>()
        val colors = ArrayList<Int>()
        val labels = ArrayList<String>()
        val redColor = Color.parseColor("#E54545")
        val greenColor = Color.parseColor("#2EAC69")
        var maxAbs = 0f

        for ((i, item) in top.withIndex()) {
            val yi = (item.mainNetInflow / 1e8).toFloat()
            entries.add(BarEntry(i.toFloat(), yi))
            colors.add(if (yi >= 0) redColor else greenColor)
            labels.add(item.name)
            maxAbs = maxOf(maxAbs, kotlin.math.abs(yi))
        }

        val dataSet = BarDataSet(entries, "")
        dataSet.colors = colors
        dataSet.valueTextSize = 11f
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

        val margin = maxAbs * 0.3f
        val minVal = entries.minOf { it.y }
        chart.axisLeft.axisMinimum = if (minVal < 0) minVal - margin else -margin
        chart.axisLeft.axisMaximum = maxOf(0f, entries.maxOf { it.y }) + margin

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                return if (idx in labels.indices) labels[idx].let { if (it.length > 5) it.substring(0, 5) else it } else ""
            }
        }
        chart.xAxis.axisMinimum = -0.5f
        chart.xAxis.axisMaximum = 14.5f

        chart.notifyDataSetChanged()
        chart.invalidate()
        chart.post { chart.animateY(300) }
    }

    override fun startObserve() {
        // 推迟到 RESUMED（窗口首帧可绘制）后再订阅：若在 onCreate 阶段立即 collect，
        // StateFlow 的 loading 态会在首帧绘制前就被消费并 show，网络稍快时 hide()
        // 也先于首帧执行，导致首次进入看不到 loading（点 Tab 时窗口早已绘制过故正常）
        lifecycleScope.launchWhenResumed {
            viewModel.viewState.collect { state ->
                // 加载状态：开始显示 loading，数据返回/失败后隐藏（与 UsStockActivity 一致）
                if (state.loading) {
                    pageLoading.show()
                    return@collect
                }
                pageLoading.hide()
                if (state.error != null) {
                    Toast.makeText(
                        this@SectorFlowActivity,
                        "加载失败：${state.error}",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@collect
                }
                // 列表 + 柱状图的重渲染推迟到 loading 收起后的下一帧，避免与 loading 动画抢占主线程
                rv_sector.post {
                    val top = if (!viewModel.isAscending) {
                        state.data
                    } else {
                        state.data.reversed()
                    }
                    sectorAdapter?.setNewInstance(top.toMutableList())
                    updateChart(state.data)
                }
            }
        }
    }
}
