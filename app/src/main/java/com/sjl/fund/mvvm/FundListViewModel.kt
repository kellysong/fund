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

                // 方法一：盘中估值用新浪 getFundInfoV2，确认净值 + 昨日涨跌幅用东方财富 lsjz
                try {
                    val fundInfo = value // 数据库已有记录，含基金名称
                    // 新浪返回为 GBK 编码，按 GBK 解码以获取正确的中文基金名称
                    val responseBody = RetrofitClient.api.getFundInfoV2(value.fundcode).await()
                    val resp = String(responseBody.source().readByteArray(), charset("GBK"))
                    // 新浪盘中估值（海外基金无数据，gsz 等为空，不影响其它字段）
                    ApiRepository.applySinaFundInfo(resp, fundInfo)
                    // 用 lsjz 填充“上次净值”区（dwjz/jzrq/jzzzl）
                    correctNetValueByLsjz(value.fundcode, fundInfo)
                    // 海外/QDII 基金新浪无数据，名称兜底用东方财富搜索接口
                    if (fundInfo.name.isEmpty()) {
                        ApiRepository.getFundNameByCode(value.fundcode)?.let { fundInfo.name = it }
                    }
                    updateFundInfo.value = fundInfo
                } catch (e: Exception) {
                    LogUtils.e("s:${value.fundcode},净值获取失败", e)
                    // 估值接口异常不再删除基金，避免误删持仓记录
                }
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
                fundInfo.dwjz = dwjz     // 确认净值
                fundInfo.jzrq = fsrq     // 确认净值日期
                fundInfo.jzzzl = jzzzl   // 确认净值日涨跌幅（昨日）
                // gsz/gszzl/gztime 保留新浪盘中估值，不再覆盖
            }
        } catch (e: Exception) {
            LogUtils.e("s:$fundCode,lsjz修正净值失败，沿用fundgz数据", e)
        }
    }


}

