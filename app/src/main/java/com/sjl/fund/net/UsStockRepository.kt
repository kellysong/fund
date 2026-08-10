package com.sjl.fund.net

import com.sjl.fund.entity.UsIndexData
import com.sjl.fund.entity.UsSectorData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.await

object UsStockRepository {

    private val INDEX_URL =
        "https://w.sinajs.cn/rn=2676345884&list=gb_dji,gb_ixic,gb_inx"

    /**
     * 加载美股指数（道琼斯/纳斯达克/标普500）
     * 返回格式：var hq_str_gb_dji="名称,现价,涨跌幅%,时间,涨跌额,..."
     * 字段[0]=名称 [1]=现价 [2]=涨跌幅 [4]=涨跌额
     */
    suspend fun loadIndexList(): List<UsIndexData> = withContext(Dispatchers.IO) {
        val resp = RetrofitClient.api.getUsIndex(INDEX_URL).await()
        val text = resp.string()
        val result = mutableListOf<UsIndexData>()
        text.lineSequence().forEach { line ->
            val match = Regex("gb_(\\w+)=\"(.*?)\"").find(line) ?: return@forEach
            val parts = match.groupValues[2].split(",")
            if (parts.size >= 5) {
                result.add(
                    UsIndexData(
                        name = parts[0],
                        price = parts[1],
                        change = parts[4],
                        changePercent = parts[2]
                    )
                )
            }
        }
        result
    }

    /**
     * 加载美股板块涨跌（新浪板块列表接口）
     * 返回 JSONP：callback({...})，data.data[] 含 category_cn/percent/total_company
     */
    suspend fun loadSectorList(): List<UsSectorData> = withContext(Dispatchers.IO) {
        val resp = RetrofitClient.api.getUsPlateList(num = 100).await()
        val raw = resp.string()

        // 剥离 JSONP 外壳：cb({...})
        val left = raw.indexOf("{")
        val right = raw.lastIndexOf("}")
        val jsonStr = if (left >= 0 && right > left) raw.substring(left, right + 1) else raw

        val root = JSONObject(jsonStr)
        val result = root.getJSONObject("result")
        val dataArr = result.getJSONObject("data").getJSONArray("data")

        val list = mutableListOf<UsSectorData>()
        for (i in 0 until dataArr.length()) {
            val item = dataArr.getJSONObject(i)
            val name = item.optString("category_cn", "").trim()
            if (name.isEmpty()) continue
            val percent = item.optString("percent", "0").toDoubleOrNull() ?: 0.0
            val count = item.optInt("total_company", 0)
            val leader = item.optString("lead_cname", "").trim()
            val leaderChg = item.optString("uppercent", "0").toDoubleOrNull() ?: 0.0
            list.add(
                UsSectorData(
                    name = name,
                    leader = leader,
                    changePercent = percent,
                    leaderChangePercent = leaderChg,
                    stockCount = count
                )
            )
        }
        list.sortedByDescending { it.changePercent }
    }
}
