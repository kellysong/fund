package com.sjl.fund.mvi

import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sjl.core.mvvm.BaseViewModelActivity
import com.sjl.fund.R
import com.sjl.fund.adapter.UsIndexAdapter
import com.sjl.fund.adapter.UsSectorAdapter
import com.sjl.fund.util.PageLoading
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

    // 页面加载 loading（统一封装，复用 PageLoading）
    private val pageLoading by lazy { PageLoading(this) }

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
        // 首屏加载：ready()/startObserve 先于 initData 执行，订阅已建立后才发起加载
        viewModel.dispatch(UsStockIntent.LoadData)
    }

    override fun startObserve() {
        // 与 SectorFlowActivity 一致：推迟到 RESUMED（窗口首帧可绘制）后订阅，
        // 避免首屏 loading 在首帧绘制前被消费/收起而不可见（不再靠接口慢侥幸）
        lifecycleScope.launchWhenResumed {
            viewModel.viewState.collect { state ->
                // 加载状态：开始显示 loading，数据返回/失败后隐藏
                if (state.loading) {
                    pageLoading.show()
                    // loading 态不重设列表：旧数据继续展示，避免无意义的全量刷新闪烁
                    return@collect
                }
                pageLoading.hide()
                indexAdapter?.setNewInstance(state.indexes)
                sectorAdapter?.setNewInstance(state.sectors)
            }
        }
    }
}
