package com.sjl.fund.mvvm

import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.sjl.core.kotlin.mvvm.BaseViewModel
import com.sjl.core.kotlin.util.log.LogUtils
import com.sjl.fund.data.FundDataSource
import com.sjl.fund.data.FundFromDb
import com.sjl.fund.data.FundFromSp
import com.sjl.fund.db.DaoRepository
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.net.ApiRepository
import com.sjl.fund.net.RetrofitClient
import com.sjl.fund.util.splitNotNull
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.lang.IllegalArgumentException


class FundListViewModel : BaseViewModel(), FundDataSource {

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
        val listFundCodeList = fundDataSource.listFundCodeList()
        if (listFundCodeList.isNullOrEmpty()) {
            finallyGlobal.value = 200
            return
        }
        launchUI({
            LogUtils.i("基金数量：${listFundCodeList.size},\t$listFundCodeList")
            for (s in listFundCodeList) {

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
                requestServer(s)

            }
        })
    }

    private suspend fun requestServer(s: String) {
        ApiRepository.getFundInfo(s, System.currentTimeMillis())
                .map {
                    val string = it.string()
                    LogUtils.i("string:${string}")
                    val start = string.indexOf("{")
                    val end = string.lastIndexOf("}") + 1
                    val fundInfo = RetrofitClient.gson.fromJson(string.substring(start, end), FundInfo::class.java)
                    fundInfo
                }.catch { e ->
                    // 异常处理
                    LogUtils.e("s:${s},请求异常", e)
                    if (e is HttpException && e.code() == 404) {
                        deleteFund(s)
                    }
                }.collect {
                    datas.value = it
                    insertFund(it)
                }


    }


    override fun listFundCodeList(): MutableList<String>? {
        val listFundCode = fundDataSource.listFundCodeList()
        return listFundCode
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


    fun saveFundCode(text: String) {
        if (TextUtils.isEmpty(text)) {
            return
        }
        launchUI({
            requestServer(text)
        })
    }

    fun refreshData() {
        LogUtils.i("定时执行")
        loadDatas()
    }

    fun sortData(data: MutableList<FundInfo>) {
        fundDataSource.sortFund(data)
    }


}

