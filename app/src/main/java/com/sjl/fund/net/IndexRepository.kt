package com.sjl.fund.net

import com.sjl.fund.entity.IndexData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.await

/**
 * 指数行情数据仓库
 * 数据来源：天天基金/东方财富 push2.eastmoney.com
 * @author Kelly
 * @version 2.0.0
 */
object IndexRepository {

    // 默认关注的四大指数 - 东方财富secid格式: 1=上海, 0=深圳
    private val DEFAULT_INDEX_SECIDS = listOf(
        "1.000001" to "上证指数",
        "0.399001" to "深证成指",
        "0.399006" to "创业板指",
        "1.000688" to "科创50"
    )

    /**
     * 加载指数行情数据
     */
    suspend fun loadIndexQuotes(): List<IndexData> {
        return withContext(Dispatchers.IO) {
            try {
                val secids = DEFAULT_INDEX_SECIDS.joinToString(",") { it.first }
                val response = RetrofitClient.api.getIndexQuotes(secids = secids).await()
                val jsonStr = response.string()
                parseIndexData(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * 解析东方财富指数数据
     * 返回格式:
     * {"data":{"total":4,"diff":[
     *   {"f2":3320.89,"f3":0.71,"f4":23.45,"f12":"000001","f14":"上证指数"},
     *   ...
     * ]}}
     * f2:最新价, f3:涨跌幅(%), f4:涨跌额, f12:代码, f14:名称
     */
    private fun parseIndexData(rawData: String): List<IndexData> {
        val result = mutableListOf<IndexData>()
        try {
            val rootJson = JSONObject(rawData)
            val dataObj = rootJson.optJSONObject("data") ?: return emptyList()
            val diffArr = dataObj.optJSONArray("diff") ?: return emptyList()

            for (i in 0 until diffArr.length()) {
                val item = diffArr.getJSONObject(i)
                val code = "sh" + item.optString("f12", "")
                val name = item.optString("f14", "--")

                // f2:最新价(需/100), f3:涨跌幅%(需/100), f4:涨跌额(需/100)
                val priceRaw = item.optDouble("f2", 0.0)
                val changePercentRaw = item.optDouble("f3", 0.0)
                val changeRaw = item.optDouble("f4", 0.0)

                // 东方财富API返回的值是实际值×100，需要除以100
                val price = priceRaw / 100.0
                val changePercent = changePercentRaw / 100.0
                val change = changeRaw / 100.0

                // 格式化显示
                val formattedPrice = String.format("%.2f", price)
                val formattedChange = if (change >= 0) String.format("+%.2f", change) else String.format("%.2f", change)
                val formattedPct = if (changePercent >= 0) String.format("+%.2f", changePercent) else String.format("%.2f", changePercent)

                result.add(IndexData(
                    name = name,
                    code = code,
                    price = formattedPrice,
                    change = formattedChange,
                    changePercent = formattedPct
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}
