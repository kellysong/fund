package com.sjl.fund.net

import com.sjl.fund.entity.SectorFlowData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.await

/**
 * 板块主力资金流向数据仓库
 * 数据来源：天天基金/东方财富 push2.eastmoney.com 板块资金流接口
 * 分类 fs 代码:
 *   行业 = m:90+t:2
 *   概念 = m:90+t:3
 *   地域 = m:90+t:1
 * @author Kelly
 * @version 1.0.0
 */
object SectorFlowRepository {

    // 板块分类 -> 东方财富 fs 过滤串（t:2=行业 t:3=概念 t:1=地域）
    val BOARD_FS_MAP = mapOf(
        "行业" to "m:90+t:2",
        "概念" to "m:90+t:3",
        "地域" to "m:90+t:1"
    )

    /**
     * 加载板块主力资金流向
     * @param fs 板块过滤串
     */
    suspend fun loadSectorFlow(fs: String): List<SectorFlowData> {
        return withContext(Dispatchers.IO) {
            val response = RetrofitClient.api.getSectorCapitalFlow(fs = fs).await()
            val raw = response.string()
            android.util.Log.d("SectorFlow", "raw len=${raw.length}")
            // 该接口返回 JSONP 格式：jsonpCallback({...})，需剥离外壳再解析
            val jsonStr = stripJsonp(raw)
            android.util.Log.d("SectorFlow", "stripped: ${jsonStr.take(200)}")
            parseSectorFlow(jsonStr)
        }
    }

    /**
     * 剥离东方财富 JSONP 外壳：jsonpCallback({...}) 或 jsonpCallback({...});
     * 若无外壳则原样返回
     */
    private fun stripJsonp(raw: String): String {
        val s = raw.trim()
        // JSONP 格式: cb({...}) 或 cb({...}); 或 jQuery123({...})
        val left = s.indexOf("{")
        val right = s.lastIndexOf("}")
        if (left >= 0 && right > left) {
            return s.substring(left, right + 1)
        }
        return s
    }

    /**
     * 解析东方财富板块资金流数据
     * 返回结构: {"data":{"total":N,"diff":[{"f12":代码,"f14":名称,"f3":涨跌幅,
     *   "f62":主力净流入,"f184":主力净占比,"f66":超大单,"f72":大单,"f78":中单,"f84":小单}]}}
     */
    private fun parseSectorFlow(rawData: String): List<SectorFlowData> {
        val result = mutableListOf<SectorFlowData>()
        val root = JSONObject(rawData)
        val rc = root.optInt("rc", -1)
        android.util.Log.d("SectorFlow", "parse rc=$rc")
        if (rc != 0) {
            android.util.Log.w("SectorFlow", "接口 rc=$rc，参数不正确。raw: ${rawData.take(300)}")
            return emptyList()
        }
        val dataObj = root.optJSONObject("data")
            ?: return emptyList()
        val diffArr = dataObj.optJSONArray("diff")
            ?: return emptyList()

        for (i in 0 until diffArr.length()) {
            val item = diffArr.getJSONObject(i)
            val code = item.optString("f12", "")
            val name = item.optString("f14", "--")
            val changePercent = item.optDouble("f3", 0.0)
            val mainNetInflow = item.optDouble("f62", 0.0)
            val mainNetInflowPercent = item.optDouble("f184", 0.0)
            val extraLarge = item.optDouble("f66", 0.0)
            val large = item.optDouble("f72", 0.0)
            val medium = item.optDouble("f78", 0.0)
            val small = item.optDouble("f84", 0.0)
            result.add(
                SectorFlowData(
                    code = code,
                    name = name,
                    changePercent = changePercent,
                    mainNetInflow = mainNetInflow,
                    mainNetInflowPercent = mainNetInflowPercent,
                    extraLargeNetInflow = extraLarge,
                    largeNetInflow = large,
                    mediumNetInflow = medium,
                    smallNetInflow = small
                )
            )
        }
        // 按主力净流入(元)降序排列
        result.sortByDescending { it.mainNetInflow }
        return result
    }
}
