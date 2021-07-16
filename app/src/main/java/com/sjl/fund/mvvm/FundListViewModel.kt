package com.sjl.fund.mvvm

import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.data.FundDataSource
import com.sjl.fund.data.FundFromDb
import com.sjl.fund.data.FundFromSp
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.net.ApiRepository
import com.sjl.fund.net.RetrofitClient
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.lang.IllegalArgumentException


class FundListViewModel : BaseViewModel(), FundDataSource {

    protected val errorGlobal by lazy { MutableLiveData<Throwable>() }

    protected val finallyGlobal by lazy { MutableLiveData<Int>() }

    /**
     * 请求失败，出现异常
     */
    fun getError(): LiveData<Throwable> {
        return errorGlobal
    }

    /**
     * 请求完成，在此处做一些关闭操作
     */
    fun getFinally(): LiveData<Int> {
        return finallyGlobal
    }

    private val datas: MutableLiveData<FundInfo> by lazy {
        MutableLiveData<FundInfo>().also {
            loadDatas()
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

    /**
     * @return LiveData<List<ArticleBean>>
     */
    fun getArticle(): LiveData<FundInfo> {
        return datas
    }

    private fun loadDatas() {
        LogUtils.i("开始加载数据")
        val listFundCodeList = listFundInfos()
        if (listFundCodeList.isNullOrEmpty()) {
            finallyGlobal.value = 200
            return
        }
        launchUI({
            LogUtils.i("基金数量：${listFundCodeList.size},\t$listFundCodeList")

            for ((index, value) in listFundCodeList.withIndex()) {

                //方法一
                /*  catchException({
                      val data = RetrofitClient.api.getFundInfo(s, System.currentTimeMillis())
                      val string = data.await().string()
                      val fundInfo =  RetrofitClient.gson.fromJson(string.substring(8, string.length - 2), FundInfo::class.java)
                      datas.value = fundInfo
  //                    LogUtils.i("s:${s},string:${string}")
                  }, { e ->
                      LogUtils.e("s:${s},请求异常", e)
                      deleteFund(s)
                  })
  */
                //方法二：转为Flow

                requestServer(value.fundcode, value.sortId, value.holdFlag, value.holdMoney)

            }
        }, { e ->
            errorGlobal.value = e
        }, {
            finallyGlobal.value = 200
        })
    }

    private suspend fun requestServer(fundCode: String, sortId: Int, holdFlag: Int, fundMoney: Double) {
        ApiRepository.getFundInfo(fundCode, System.currentTimeMillis())
                .map {
                    val string = it.string()
                    LogUtils.i("string:${string}")
                    val start = string.indexOf("{")
                    val end = string.lastIndexOf("}") + 1
                    val fundInfo = RetrofitClient.gson.fromJson(string.substring(start, end), FundInfo::class.java)
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
                    datas.value = it
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
        datas.value = fundInfo
    }


}

