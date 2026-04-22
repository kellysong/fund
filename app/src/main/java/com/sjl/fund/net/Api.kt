package com.sjl.fund.net

import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 *
 * @author Kelly
 * @version 1.0.0
 * @filename Api
 * @time 2021/1/7 12:22
 * @copyright(C) 2021 song
 */
interface Api {

    /**
     * 返回Call<ResponseBody>，不能加suspend
     * http://fundgz.1234567.com.cn/js/001970.js?rt=1463558676006"
     * jsonpgz({"fundcode":"001970","name":"泰信鑫选灵活配置混合A","jzrq":"2021-04-09","dwjz":"1.1610","gsz":"1.1406","gszzl":"-1.76","gztime":"2021-04-12 15:00"});
     * @param code String
     * @param rt Long
     * @return ResponseData<List<ArticleBean>> okhttp3.ResponseBody
     */
    @GET("js/{code}.js")
    fun getFundInfo(@Path("code") code: String, @Query("rt") rt: Long): Call<ResponseBody>

    /**
     * 所有基金名称列表代码
     * @return Call<ResponseBody>
     */
    @GET("http://fund.eastmoney.com/js/fundcode_search.js")
    fun searchFundList(): Call<ResponseBody>

    /**
     * 获取基金历史净值
     * http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code=001970&page=1&per=20
     * @param code 基金代码
     * @param page 页码
     * @param per 每页条数
     */
    @GET("http://fund.eastmoney.com/f10/F10DataApi.aspx")
    fun getFundHistoryNetValue(
        @Query("type") type: String = "lsjz",
        @Query("code") code: String,
        @Query("page") page: Int = 1,
        @Query("per") per: Int = 20
    ): Call<ResponseBody>

    /**
     * 获取基金业绩走势（单位净值走势）
     * http://fund.eastmoney.com/pingzhongdata/001970.js
     * @param code 基金代码
     */
    @GET("http://fund.eastmoney.com/pingzhongdata/{code}.js")
    fun getFundPerformanceTrend(@Path("code") code: String): Call<ResponseBody>

    /**
     * 获取基金持仓信息
     * http://fund.eastmoney.com/f10/FundArchivesDatas.aspx?type=jjcc&code=001970&topline=10
     * @param code 基金代码
     * @param topline 返回条数
     */
    @GET("http://fund.eastmoney.com/f10/FundArchivesDatas.aspx")
    fun getFundHoldings(
        @Query("type") type: String = "jjcc",
        @Query("code") code: String,
        @Query("topline") topline: Int = 10
    ): Call<ResponseBody>

    /**
     * 获取基金实时估值
     * http://fundgz.1234567.com.cn/js/001970.js?rt=1463558676006
     * 与getFundInfo相同，但用于明确获取实时估值场景
     * @param code 基金代码
     * @param rt 时间戳
     */
    @GET("http://fundgz.1234567.com.cn/js/{code}.js")
    fun getFundRealTimeValue(@Path("code") code: String, @Query("rt") rt: Long): Call<ResponseBody>
}