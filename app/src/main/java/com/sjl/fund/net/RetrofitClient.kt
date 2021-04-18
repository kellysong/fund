package com.sjl.fund.net

import com.google.gson.Gson
import com.sjl.core.kotlin.net.RetrofitHelper

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename RetrofitClient
 * @time 2021/1/7 12:25
 * @copyright(C) 2021 泰中科技
 */
 object RetrofitClient {

    val api by lazy {
        return@lazy RetrofitHelper.getInstance().getApiService(Api::class.java)
    }
    var gson = Gson()

}