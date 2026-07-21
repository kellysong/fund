package com.sjl.fund.net

import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
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
     * 基金净值获取接口（替代已下架的 getFundInfo / fundgz 实时估值接口）
     * 使用新浪财经行情接口 fu_，返回盘中实时估值、估值涨跌幅与更新时间，
     * 满足盘中净值展示需求（天天基金官方实时估值已下线，改用新浪）。
     * 注意：新浪返回为 GBK 编码，数字字段不受影响；需携带 Referer 否则 403。
     */
    @GET("https://hq.sinajs.cn/list=fu_{code}")
    @Headers(
        "Referer: https://finance.sina.com.cn/",
        "User-Agent: Mozilla/5.0"
    )
    fun getFundInfoV2(@Path("code") code: String): Call<ResponseBody>

    /**
     * 所有基金名称列表代码
     * @return Call<ResponseBody>
     */
    @GET("http://fund.eastmoney.com/js/fundcode_search.js")
    fun searchFundList(): Call<ResponseBody>

    /**
     * 按基金代码查询基金名称（东方财富搜索接口，支持海外/QDII 基金）
     * https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=016702
     * 返回 JSON：{"Datas":[{"CODE":"016702","NAME":"银华海外数字经济...","FundBaseInfo":{...}}],...}
     * 用途：新浪 fu_ 接口不覆盖海外基金，拿不到名称时，用此接口兜底。
     */
    @GET("https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx")
    @Headers(
        "Referer: https://fund.eastmoney.com/",
        "User-Agent: Mozilla/5.0"
    )
    fun getFundNameByCode(
        @Query("m") m: Int = 1,
        @Query("key") key: String
    ): Call<ResponseBody>

    /**
     * 获取基金历史净值（东方财富官方 JSON 接口，稳定可用）
     * https://api.fund.eastmoney.com/f10/lsjz?fundCode=001970&pageIndex=1&pageSize=30
     * 返回 JSON：{"Data":{"LSJZList":[{"FSRQ","DWJZ","LJJZ","JZZZL",...}],"TotalCount":N},...}
     * 注意：必须带 Referer 头，否则接口返回空数据
     *
     * @param fundCode 基金代码
     * @param pageIndex 页码（从1开始）
     * @param pageSize 每页条数
     * @param startDate 开始日期 yyyy-MM-dd（可选，留空为不限）
     * @param endDate 结束日期 yyyy-MM-dd（可选，留空为不限）
     * @param ts 时间戳，避免缓存
     */
    @GET("https://api.fund.eastmoney.com/f10/lsjz")
    @Headers(
        "Referer: https://fund.eastmoney.com/",
        "User-Agent: Mozilla/5.0"
    )
    fun getFundHistoryNetValue(
        @Query("fundCode") fundCode: String,
        @Query("pageIndex") pageIndex: Int = 1,
        @Query("pageSize") pageSize: Int = 30,
        @Query("startDate") startDate: String = "",
        @Query("endDate") endDate: String = "",
        @Query("_") ts: Long = System.currentTimeMillis()
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
    @Headers("Referer: https://fund.eastmoney.com/")
    fun getFundHoldings(
        @Query("type") type: String = "jjcc",
        @Query("code") code: String,
        @Query("year") year: Int = 0,
        @Query("month") month: Int = 0,
        @Query("topline") topline: Int = 10
    ): Call<ResponseBody>

    /**
     * 获取基金资产配置（股票/债券/现金/其它占比）
     * https://fundf10.eastmoney.com/zcpz_001970.html  （注意是下划线 zcpz_{code}.html，不是 zcpz/{code}.html）
     * 返回完整 HTML 页面，内含 var chartData = {Dates:[...],GP:[...],ZQ:[...],XJ:[...],CTPZ:[...],...}
     * GP=股票, ZQ=债券, XJ=现金, CTPZ=其它
     */
    @GET("https://fundf10.eastmoney.com/zcpz_{code}.html")
    @Headers("Referer: https://fundf10.eastmoney.com/")
    fun getAssetAllocation(@Path("code") code: String): Call<ResponseBody>

    /**
     * 获取基金实时估值
     * http://fundgz.1234567.com.cn/js/001970.js?rt=1463558676006
     * 与getFundInfo相同，但用于明确获取实时估值场景
     * @param code 基金代码
     * @param rt 时间戳
     */
    @GET("http://fundgz.1234567.com.cn/js/{code}.js")
    fun getFundRealTimeValue(@Path("code") code: String, @Query("rt") rt: Long): Call<ResponseBody>

    /**
     * 获取实时行情（新浪财经通用接口 - 支持指数和股票）
     * 指数: http://hq.sinajs.cn/list=sh000001,sz399001
     * 股票: http://hq.sinajs.cn/list=sh601398,sz000001
     * @param codes 代码列表，逗号分隔
     */
    /**
     * 获取指数实时行情（天天基金/东方财富）
     * http://push2.eastmoney.com/api/qt/ulist.np/get?fields=f2,f3,f4,f12,f14&secids=1.000001,0.399001,0.399006,1.000688
     * f2:最新价, f3:涨跌幅, f4:涨跌额, f12:代码, f14:名称
     */
    @GET("http://push2.eastmoney.com/api/qt/ulist.np/get")
    fun getIndexQuotes(
        @Query("fields") fields: String = "f2,f3,f4,f12,f14",
        @Query("secids") secids: String
    ): Call<ResponseBody>

    /**
     * 获取板块主力资金流向（行业/概念/地域）
     * https://push2.eastmoney.com/api/qt/clist/get?fs=m:90+t:2
     * fs 板块类型: 行业=m:90+t:2, 概念=m:90+t:3, 地域=m:90+t:1
     * fields: f12=代码, f14=名称, f3=涨跌幅, f62=主力净流入, f184=主力净占比,
     *         f66=超大单, f72=大单, f78=中单, f84=小单
     */
    @GET("http://push2.eastmoney.com/api/qt/clist/get")
    @Headers("User-Agent: Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36")
    fun getSectorCapitalFlow(
        @Query("cb") cb: String = "randomCallback",
        @Query("pn") pn: Int = 1,
        @Query("pz") pz: Int = 200,
        @Query("po") po: Int = 1,
        @Query("np") np: Int = 1,
        @Query("fltt") fltt: Int = 2,
        @Query("invt") invt: Int = 2,
        @Query("ut") ut: String = "8dec03ba335b81bf4ebdf7f7426281",
        @Query("fid") fid: String = "f62",
        @Query(value = "fs", encoded = true) fs: String,
        @Query("fields") fields: String = "f12,f14,f2,f3,f62,f184,f66,f72,f78,f84"
    ): Call<ResponseBody>

    /**
     * 新浪财经行情接口需带 Referer，否则返回 403 Forbidden
     */
    @GET
    @Headers("Referer: https://finance.sina.com.cn")
    fun getSinaQuotes(@retrofit2.http.Url url: String): Call<ResponseBody>
}