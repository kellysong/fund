package com.sjl.fund.net

import com.sjl.fund.entity.FundInfo
import com.sjl.fund.net.RetrofitClient
import com.sjl.core.util.log.LogUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import org.json.JSONObject
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

    /**
     * 替代已下架的 getFundInfo（fundgz 实时估值接口）的 Flow 版本。
     * 使用东方财富官方历史净值接口 lsjz 获取最新净值。
     */
    fun getFundInfoV2(code: String): Flow<ResponseBody> {
        return flow {
            emit(RetrofitClient.api.getFundInfoV2(code).await())
        }
    }

    /**
     * 将新浪行情接口（fu_）返回的盘中估值应用到已有的 FundInfo 对象（保留 name 等原有字段）。
     * 返回文本形如：var hq_str_fu_xxxxxx="名称,时间,盘中估值,昨收净值,今开,成交量,估值涨跌幅%,日期,...";
     * 字段说明：parts[1]=更新时间, parts[2]=盘中估算净值(gsz), parts[3]=昨收净值(最新净值近似),
     *          parts[6]=估值涨跌幅%, parts[7]=净值日期
     * @return true 表示解析并写入成功
     */
    fun applySinaFundInfo(text: String, fundInfo: FundInfo): Boolean {
        return try {
            val start = text.indexOf("\"")
            val end = text.lastIndexOf("\"")
            if (start < 0 || end <= start) return false
        val parts = text.substring(start + 1, end).split(",")
        if (parts.size < 8) return false
        // 基金名称：新浪返回含名称（GBK 编码），仅当原对象无名称时补全（首次添加场景）
        if (fundInfo.name.isEmpty() && parts.isNotEmpty()) {
            fundInfo.name = parts[0].trim()
        }
        val gsz = parts[2]
            val gszzl = try {
                // 盘中估值涨跌幅保留两位小数
                String.format("%.2f", parts[6].toDouble())
            } catch (e: Exception) {
                parts[6]
            }
            val gztime = parts[1]   // 估值更新时间（时:分:秒）
            val gzdate = parts[7]   // 估值日期（年-月-日）
            if (gsz.isEmpty()) return false
            // 只填充“盘中估值”相关字段（gsz/gszzl/gztime）。
            // 确认净值与昨日涨跌幅(dwjz/jzrq/jzzzl)由 getFundHistoryNetValue(lsjz) 提供，不要在此覆盖。
            fundInfo.gsz = gsz
            fundInfo.gszzl = gszzl
            // 更新时间显示为“日期 + 时间”
            fundInfo.gztime = if (gzdate.isNotEmpty()) "$gzdate $gztime" else gztime
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 按基金代码查询基金名称（东方财富搜索接口）。
     * 用于新浪 fu_ 不覆盖的海外/QDII 基金，作为名称兜底来源。
     * @return 基金名称；查询失败或不存在时返回 null
     */
    suspend fun getFundNameByCode(code: String): String? {
        return try {
            val resp = RetrofitClient.api.getFundNameByCode(key = code).await().string()
            val root = JSONObject(resp)
            val datas = root.optJSONArray("Datas") ?: return null
            if (datas.length() == 0) return null
            val obj = datas.getJSONObject(0)
            val name = obj.optString("NAME", "")
            if (name.isNotEmpty()) name else null
        } catch (e: Exception) {
            LogUtils.e("s:$code,查询基金名称失败", e)
            null
        }
    }
}