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
import org.json.JSONObject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import retrofit2.await
import java.lang.IllegalArgumentException


class FundListViewModel : BaseViewModel(), FundDataSource {
    val dataSourceType = 0
    val errorGlobal by lazy { MutableLiveData<Throwable>() }

   val finallyGlobal by lazy { MutableLiveData<Int>() }

    val updateFundInfo: MutableLiveData<FundInfo> by lazy {
        MutableLiveData<FundInfo>().also {
            loadDatas()
        }
    }
    val listFundInfos: MutableLiveData<List<FundInfo>> by lazy {
        MutableLiveData<List<FundInfo>>()
    }


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
        val listFundCodeList = listFundInfos()
        if (listFundCodeList.isNullOrEmpty()) {
            finallyGlobal.value = 200
            return
        }
        listFundInfos.value  = listFundCodeList

        launchUI({
            LogUtils.i("基金数量：${listFundCodeList.size},\t$listFundCodeList")

            for ((index, value) in listFundCodeList.withIndex()) {

                //方法一
                  catchException({
                      val data = RetrofitClient.api.getFundInfo(value.fundcode, System.currentTimeMillis())
                      val string = data.await().string()
                      val fundInfo =  RetrofitClient.gson.fromJson(string.substring(8, string.length - 2), FundInfo::class.java)
                      // 用官方历史净值接口 lsjz 修正当前净值（fundgz 的 dwjz 滞后、gsz 为估值，不准）
                      correctNetValueByLsjz(value.fundcode, fundInfo)
                      updateFundInfo.value = fundInfo
                      LogUtils.i("s:${value.fundcode},string:${string}")
                  }, { e ->
                      LogUtils.e("s:${value.fundcode},请求异常", e)
                      deleteFund(value.fundcode)
                  })
                //方法二：转为Flow

//                requestServer(value.fundcode, value.sortId, value.holdFlag, value.holdMoney)

            }
        }, {
            finallyGlobal.value = 200
        }, { e ->
            errorGlobal.value = e
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
                    updateFundInfo.value = it
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
        updateFundInfo.value = fundInfo
    }

    /**
     * 用官方历史净值接口 lsjz 的最新一条覆盖 fundgz 返回的净值字段。
     * fundgz 的 dwjz 常滞后一天、gsz 为盘中估值，与支付宝单位净值对不上；
     * lsjz 已验证与支付宝一致（返回 Data.LSJZList[0]：FSRQ/DWJZ/JZZZL）。
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
                fundInfo.dwjz = dwjz
                fundInfo.gsz = dwjz
                fundInfo.gszzl = jzzzl
                fundInfo.jzrq = fsrq
            }
        } catch (e: Exception) {
            LogUtils.e("s:$fundCode,lsjz修正净值失败，沿用fundgz数据", e)
        }
    }


}

