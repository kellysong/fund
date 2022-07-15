package com.sjl.fund.mvi

import android.text.TextUtils
import androidx.lifecycle.viewModelScope
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.data.FundDataSource
import com.sjl.fund.data.FundFromDb
import com.sjl.fund.data.FundFromSp
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.net.ApiRepository
import com.sjl.fund.net.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename FundListViewModel2
 * @time 2022/5/18 16:49
 * @copyright(C) 2021 song
 */
class FundListViewModel2 : BaseViewModel(), FundDataSource {

    private val _viewState = MutableSharedFlow<FundListUiState>()
    val viewState: SharedFlow<FundListUiState>
        get() = _viewState

    /**
     * 接收事件
     */
    private val userIntent = MutableSharedFlow<FundListIntent>()

    init {
        viewModelScope.launch {
            userIntent.collect {
                when (it) {
//                    is FundListIntent.InitData -> listFundInfos()
                    is FundListIntent.RefreshData -> refreshData()
                    is FundListIntent.SortData -> sortData(it.data)
                    is FundListIntent.DeleteFund -> deleteFund(it.fundCode)
                    is FundListIntent.Update -> update(it.fundInfo)
                    is FundListIntent.SaveFundCode -> saveFundCode(
                        it.fundCode,
                        it.holdFlag,
                        it.fundMoney
                    )
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
            val listFundCodeList = listFundInfos()
            val name = Thread.currentThread().name
            LogUtils.i(name)
            _viewState.emit(FundListUiState.InitSuccess(listFundCodeList))

            listFundCodeList?.let {
                LogUtils.i("基金数量：${it.size},\t$it")
                for ((index, value) in it.withIndex()) {
                    requestServer(value.fundcode, value.sortId, value.holdFlag, value.holdMoney)
                }
            }

        }, {
            _viewState.emit(FundListUiState.LoadFinish(200))
        }, { e ->
            _viewState.emit(FundListUiState.LoadError(e))
        })
    }

    private suspend fun requestServer(
        fundCode: String,
        sortId: Int,
        holdFlag: Int,
        fundMoney: Double
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
                _viewState.emit(FundListUiState.LoadSuccess(it))
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


    fun saveFundCode(fundCode: String, holdFlag: Int, fundMoney: Double) {
        if (TextUtils.isEmpty(fundCode)) {
            return
        }
        launchUI({
            requestServer(fundCode, getMaxSortId(), holdFlag, fundMoney)
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
            _viewState.emit(FundListUiState.LoadSuccess(fundInfo))
        })


    }


}

sealed class FundListIntent {
//    object InitData : FundListIntent()
    object RefreshData : FundListIntent()
    data class SortData(val data: MutableList<FundInfo>) : FundListIntent()
    data class DeleteFund(val fundCode: String) : FundListIntent()
    data class Update(val fundInfo: FundInfo) : FundListIntent()
    data class SaveFundCode(val fundCode: String, val holdFlag: Int, val fundMoney: Double) :
        FundListIntent()
}


sealed class FundListUiState {

    data class InitSuccess(val resData: MutableList<FundInfo>?) : FundListUiState()
    data class LoadError(val error: Throwable) : FundListUiState()
    data class LoadFinish(val code: Int) : FundListUiState()
    data class LoadSuccess(val resData: FundInfo) : FundListUiState()

}

