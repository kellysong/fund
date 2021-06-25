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
    @GET("http://fundgz.1234567.com.cn/js/{code}.js")
    fun getFundInfo(@Path("code") code: String, @Query("rt") rt: Long): Call<ResponseBody>

    /**
     * 所有基金名称列表代码
     * @return Call<ResponseBody>
     */
    @GET("http://fund.eastmoney.com/js/fundcode_search.js")
    fun searchFundList(): Call<ResponseBody>
}