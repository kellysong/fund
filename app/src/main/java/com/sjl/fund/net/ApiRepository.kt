package com.sjl.fund.net

import com.sjl.fund.net.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.await
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename ApiRepository
 * @time 2021/4/13 16:54
 * @copyright(C) 2021 song
 */
object ApiRepository {
    /**
     * 转换为Flow,方便处理数据
     * 或者使用https://github.com/chenxyu/retrofit-adapter-flow/blob/master/app/src/main/java/com/chenxyu/example/MainActivity.kt
     * @param code String
     * @param rt Long
     * @return Flow<Call<ResponseBody>>
     */
    fun getFundInfo(code: String,rt: Long): Flow<ResponseBody> {
        return flow {
            emit(RetrofitClient.api.getFundInfo(code,rt).await())
        }
    }
}