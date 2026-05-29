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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

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
                        it.fundType
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
            val listFundCodeList = listFundInfosByType(currentFundType)
            val name = Thread.currentThread().name
            LogUtils.i(name)
            _viewState.emit(FundListUiState.InitSuccess(listFundCodeList))

            listFundCodeList?.let {
                LogUtils.i("基金数量：${it.size},\t$it")
                for ((index, value) in it.withIndex()) {
                    requestServer(value.fundcode, value.sortId, value.holdFlag, value.holdMoney, value.fundType)
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
        fundType: Int
    ) {
        ApiRepository.getFundInfo(fundCode, System.currentTimeMillis())
            .map {
                val string = it.string()
                LogUtils.i("string:${string}")
                val start = string.indexOf("{")
                val end = string.lastIndexOf("}") + 1
                val fundInfo =
                    RetrofitClient.gson.fromJson(string.substring(start, end), FundInfo::class.java)
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
                _viewState.emit(FundListUiState.LoadSuccess(it, fundType))
                insertFund(it)
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


    fun saveFundCode(fundCode: String, holdFlag: Int, fundMoney: Double, fundType: Int) {
        if (TextUtils.isEmpty(fundCode)) {
            return
        }
        launchUI({
            requestServer(fundCode, 0, holdFlag, fundMoney, fundType)
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
    data class SaveFundCode(val fundCode: String, val holdFlag: Int, val fundMoney: Double, val fundType: Int = 0) :
        FundListIntent()
}


sealed class FundListUiState {

    data class InitSuccess(val resData: MutableList<FundInfo>?) : FundListUiState()
    data class LoadError(val error: Throwable) : FundListUiState()
    data class LoadFinish(val code: Int) : FundListUiState()
    data class LoadSuccess(val resData: FundInfo, val fundType: Int = 0) : FundListUiState()
    data class IndexDataLoaded(val indexList: List<IndexData>) : FundListUiState()

}
