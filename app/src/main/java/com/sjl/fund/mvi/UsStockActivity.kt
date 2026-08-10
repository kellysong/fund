package com.sjl.fund.mvi

import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.fund.R
import com.sjl.fund.adapter.UsIndexAdapter
import com.sjl.fund.adapter.UsSectorAdapter
import kotlinx.android.synthetic.main.us_stock_activity.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 美股行情页面（指数 + 板块涨跌）
 * 采用 MVI 模式，参考 FundListActivity2 / SectorFlowActivity
 * @author Kelly
 * @version 1.0.0
 */
class UsStockActivity : BaseViewModelActivity<UsStockViewModel>() {

    override fun getLayoutId(): Int = R.layout.us_stock_activity

    override fun providerVMClass(): Class<UsStockViewModel>? = UsStockViewModel::class.java

    private var indexAdapter: UsIndexAdapter? = null
    private var sectorAdapter: UsSectorAdapter? = null

    override fun initView() {
        // 返回
        iv_back.setOnClickListener { finish() }
        // 刷新
        iv_refresh.setOnClickListener {
            viewModel.dispatch(UsStockIntent.Refresh)
        }

        // 板块排序（点击图标切换升降序，仅本地重排）
        iv_sort.setOnClickListener {
            viewModel.sectorAscending = !viewModel.sectorAscending
            viewModel.dispatch(UsStockIntent.SortSector)
        }

        // 指数横向列表
        rv_index.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        indexAdapter = UsIndexAdapter()
        rv_index.adapter = indexAdapter

        // 板块列表
        rv_sector.layoutManager = LinearLayoutManager(this)
        sectorAdapter = UsSectorAdapter()
        rv_sector.adapter = sectorAdapter
    }

    override fun initListener() {
    }

    override fun initData() {
    }

    override fun startObserve() {
        lifecycleScope.launch {
            viewModel.viewState.collect { state ->
                indexAdapter?.setNewInstance(state.indexes)
                sectorAdapter?.setNewInstance(state.sectors)
            }
        }
    }
}
