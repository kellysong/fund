package com.sjl.fund.mvi

import android.text.TextUtils
import androidx.lifecycle.viewModelScope
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.data.FundDataSource
import com.sjl.fund.data.FundFromDb
import com.sjl.fund.data.FundFromSp
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.entity.IndexData
import com.sjl.fund.net.ApiRepository
import com.sjl.fund.net.IndexRepository
import com.sjl.fund.net.RetrofitClient
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.await

/**
 * 主页ViewModel - 支持双Tab和指数行情
 * @author Kelly
 * @version 2.0.0
 */
class FundListViewModel2 : BaseViewModel(), FundDataSource {

    private val _viewState = MutableSharedFlow<FundListUiState>()
    val viewState: SharedFlow<FundListUiState>
        get() = _viewState

    /**
     * 接收事件
     */
    private val userIntent = MutableSharedFlow<FundListIntent>()

    // 当前选中Tab: 0=自选, 1=其他基金
    var currentFundType = 0

    init {
        viewModelScope.launch {
            userIntent.collect {
                when (it) {
                    is FundListIntent.RefreshData -> refreshData()
                    is FundListIntent.SortData -> sortData(it.data)
                    is FundListIntent.DeleteFund -> deleteFund(it.fundCode)
                    is FundListIntent.Update -> update(it.fundInfo)
                    is FundListIntent.SaveFundCode -> saveFundCode(
                        it.fundCode,
                        it.holdFlag,
                        it.fundMoney,
                        it.fundType,
                        it.operateType
                    )
                    is FundListIntent.LoadIndexData -> loadIndexData()
                    is FundListIntent.SwitchTab -> {
                        currentFundType = it.fundType
                    }
                    else -> {}
                }
            }
        }
    }


    /**
     * 分发用户事件
     * @param viewAction
     */
    fun dispatch(viewAction: FundListIntent) {
        try {
            viewModelScope.launch {
                userIntent.emit(viewAction)
            }
        } catch (e: Exception) {
        }
    }


    val dataSourceType = 0
    val fundDataSource: FundDataSource by lazy {

        when (dataSourceType) {
            0 -> FundFromDb()
            1 -> FundFromSp()
            else -> {
                throw IllegalArgumentException("非法数据源")
            }
        }
    }


    private fun loadDatas() {
        LogUtils.i("开始加载数据")

        launchUI({
            val listFundInfos = listFundInfos()
            LogUtils.i("基金列表：${listFundInfos?.size}")
            val listFundCodeList = listFundInfosByType(currentFundType)
            val name = Thread.currentThread().name
            LogUtils.i(name)
            _viewState.emit(FundListUiState.InitSuccess(listFundCodeList))

            listFundCodeList?.let {
                LogUtils.i("基金数量：${it.size},\t$it")
                for ((index, value) in it.withIndex()) {
                    requestServer(value.fundcode, value.sortId, value.holdFlag, value.holdMoney, value.fundType, value.createTime,value.operateType)
                }
            }

        }, {
            _viewState.emit(FundListUiState.LoadFinish(200))
        }, { e ->
            _viewState.emit(FundListUiState.LoadError(e))
        })
    }

    /**
     * 加载指数行情数据
     */
    private fun loadIndexData() {
        launchUI({
            val indexList = IndexRepository.loadIndexQuotes()
            _viewState.emit(FundListUiState.IndexDataLoaded(indexList))
        }, {}, { e ->
            LogUtils.e("加载指数数据失败", e)
        })
    }

    private suspend fun requestServer(
        fundCode: String,
        sortId: Int,
        holdFlag: Int,
        fundMoney: Double,
        fundType: Int,
        createTime: Long = 0L,
        operateType: Int
    ) {
        ApiRepository.getFundInfo(fundCode, System.currentTimeMillis())
            .map {
                val string = it.string()
                LogUtils.i("string:${string}")
                val start = string.indexOf("{")
                val end = string.lastIndexOf("}") + 1
                val fundInfo =
                    RetrofitClient.gson.fromJson(string.substring(start, end), FundInfo::class.java)
                // 用官方历史净值接口 lsjz 修正当前净值（fundgz 滞后/估值为准）
                correctNetValueByLsjz(fundCode, fundInfo)
                fundInfo
            }.catch { e ->
                // 异常处理
                LogUtils.e("s:${fundCode},请求异常", e)
                if (e is HttpException && e.code() == 404) {
                    deleteFund(fundCode)
                }
            }.collect {
                it.sortId = sortId
                it.holdFlag = holdFlag
                it.holdMoney = fundMoney
                it.fundType = fundType
                it.createTime = createTime
                it.operateType = operateType
                _viewState.emit(FundListUiState.LoadSuccess(it, fundType))
                insertFund(it)
            }


    }

    /**
     * 用官方历史净值接口 lsjz 的最新一条修正 FundInfo 的确认净值字段。
     * fundgz 的 dwjz 常滞后一天、gsz 为盘中估值，与支付宝单位净值对不上；
     * lsjz 已验证与支付宝一致。不再覆盖 gsz/gszzl（盘中估值保留 fundgz 原始值）。
     */
    private suspend fun correctNetValueByLsjz(fundCode: String, fundInfo: FundInfo) {
        try {
            val resp = RetrofitClient.api.getFundHistoryNetValue(
                fundCode = fundCode, pageSize = 1
            ).await()
            val root = JSONObject(resp.string())
            val data = root.optJSONObject("Data") ?: return
            val lsjz = data.optJSONArray("LSJZList") ?: return
            if (lsjz.length() == 0) return
            val obj = lsjz.getJSONObject(0)
            val dwjz = obj.optString("DWJZ", "")
            val jzzzl = obj.optString("JZZZL", "")
            val fsrq = obj.optString("FSRQ", "")
            if (dwjz.isNotEmpty()) {
                fundInfo.dwjz = dwjz     // 确认净值
                fundInfo.jzrq = fsrq     // 确认净值日期
                fundInfo.jzzzl = jzzzl   // 确认净值日涨跌幅
                // gsz/gszzl/gztime 保持 fundgz 盘中估值，不再覆盖
            }
        } catch (e: Exception) {
            LogUtils.e("s:$fundCode,lsjz修正净值失败，沿用fundgz数据", e)
        }
    }


    override fun listFundCodeList(): MutableList<String>? {
        val listFundCode = fundDataSource.listFundCodeList()
        return listFundCode
    }

    override fun listFundInfos(): MutableList<FundInfo>? {
        return fundDataSource.listFundInfos()
    }

    override fun listFundInfosByType(fundType: Int): MutableList<FundInfo>? {
        return fundDataSource.listFundInfosByType(fundType)
    }


    @Synchronized
    override fun deleteFund(fundCode: String) {
        if (TextUtils.isEmpty(fundCode)) {
            return
        }
        fundDataSource.deleteFund(fundCode)

    }

    override fun sortFund(data: MutableList<FundInfo>) {
        fundDataSource.sortFund(data)
    }

    override fun insertFund(fundInfo: FundInfo) {
        fundDataSource.insertFund(fundInfo)
    }

    override fun getMaxSortId(): Int {
        return fundDataSource.getMaxSortId() + 1
    }


    fun saveFundCode(fundCode: String, holdFlag: Int, fundMoney: Double, fundType: Int, operateType: Int) {
        if (TextUtils.isEmpty(fundCode)) {
            return
        }
        launchUI({
            val newSortId = getMaxSortId()
            requestServer(fundCode, newSortId, holdFlag, fundMoney, fundType, System.currentTimeMillis(),operateType)
        })
    }

    fun refreshData() {
        LogUtils.i("定时执行")
        loadDatas()
    }

    fun sortData(data: MutableList<FundInfo>) {
        fundDataSource.sortFund(data)
    }


    fun update(fundInfo: FundInfo) {
        insertFund(fundInfo)
        launchUI({
            _viewState.emit(FundListUiState.LoadSuccess(fundInfo, fundInfo.fundType))
        })


    }


}

sealed class FundListIntent {
    object RefreshData : FundListIntent()
    object LoadIndexData : FundListIntent()
    data class SwitchTab(val fundType: Int) : FundListIntent()
    data class SortData(val data: MutableList<FundInfo>) : FundListIntent()
    data class DeleteFund(val fundCode: String) : FundListIntent()
    data class Update(val fundInfo: FundInfo) : FundListIntent()
    data class SaveFundCode(val fundCode: String, val holdFlag: Int, val fundMoney: Double, val fundType: Int = 0, val operateType: Int = 0) :
        FundListIntent()
}


sealed class FundListUiState {

    data class InitSuccess(val resData: MutableList<FundInfo>?) : FundListUiState()
    data class LoadError(val error: Throwable) : FundListUiState()
    data class LoadFinish(val code: Int) : FundListUiState()
    data class LoadSuccess(val resData: FundInfo, val fundType: Int = 0) : FundListUiState()
    data class IndexDataLoaded(val indexList: List<IndexData>) : FundListUiState()

}
