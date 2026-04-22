package com.sjl.fund.mvvm

import androidx.lifecycle.MutableLiveData
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.entity.FundHistoryNetValue
import com.sjl.fund.entity.FundHolding
import com.sjl.fund.entity.FundPerformance
import com.sjl.fund.entity.FundTrendPoint
import com.sjl.fund.net.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * 基金详情ViewModel
 * @author Kelly
 * @version 2.0.0
 * @filename FundDetailViewModel
 * @time 2026/4/9
 */
class FundDetailViewModel : BaseViewModel() {

    val historyNetValues = MutableLiveData<List<FundHistoryNetValue>>()
    // 完整走势数据（按时间戳排序），由Activity按需截取
    val allTrendPoints = MutableLiveData<List<FundTrendPoint>>()
    val performances = MutableLiveData<List<FundPerformance>>()
    val holdings = MutableLiveData<List<FundHolding>>()
    val netValueInfo = MutableLiveData<Triple<String, String, String>>() // 单位净值/累计净值/日期
    val realTimeValue = MutableLiveData<Triple<String, String, String>>() // 估值/涨跌幅/估值时间
    val error = MutableLiveData<Throwable>()
    val loading = MutableLiveData<Boolean>()

    // ---- 时间段定义 ----
    companion object {
        val PERIOD_LIST = listOf(
            Triple("1m",  "近1月",  30),
            Triple("3m",  "近3月",  90),
            Triple("6m",  "近6月",  180),
            Triple("1y",  "近1年",  365),
            Triple("3y",  "近3年",  1095),
            Triple("5y",  "近5年",  1825),
            Triple("ls",  "成立以来", Int.MAX_VALUE)
        )
    }

    fun loadFundDetail(fundCode: String) {
        loading.value = true
        launchUI({
            try {
                withContext(Dispatchers.IO) {
                    loadHistoryNetValue(fundCode)
                    loadPerformanceTrendAndPerformance(fundCode)
                    loadHoldings(fundCode)
                    loadRealTimeValue(fundCode)
                }
            } catch (e: Exception) {
                LogUtils.e("加载基金详情失败", e)
                error.postValue(e)
            } finally {
                loading.postValue(false)
            }
        })
    }

    /**
     * 加载实时估值
     */
    private suspend fun loadRealTimeValue(fundCode: String) {
        try {
            val response = RetrofitClient.api.getFundInfo(fundCode, System.currentTimeMillis()).await()
            val jsonStr = response.string()

            // 解析JSONP格式: jsonpgz({...})
            val pattern = Pattern.compile("jsonpgz\\((\\{.*?\\})\\);")
            val matcher = pattern.matcher(jsonStr)

            if (matcher.find()) {
                val jsonData = matcher.group(1)
                val jsonObject = JSONObject(jsonData)
                val gsz = jsonObject.optString("gsz", "--")      // 估算净值
                val gszzl = jsonObject.optString("gszzl", "--") // 估算涨跌幅
                val gztime = jsonObject.optString("gztime", "--") // 估值时间

                realTimeValue.postValue(Triple(gsz, gszzl, gztime))
            }
        } catch (e: Exception) {
            LogUtils.e("获取实时估值失败", e)
        }
    }

    // ---- 历史净值 ----
    private suspend fun loadHistoryNetValue(fundCode: String) {
        try {
            val response = RetrofitClient.api.getFundHistoryNetValue(code = fundCode, per = 30).await()
            val jsonStr = response.string()

            val pattern = Pattern.compile("var apidata\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL)
            val matcher = pattern.matcher(jsonStr)
            if (matcher.find()) {
                val jsonObject = JSONObject(matcher.group(1))
                val content = jsonObject.optString("content", "")
                val list = parseHistoryNetValueFromHtml(content)
                historyNetValues.postValue(list)
                if (list.isNotEmpty()) {
                    val latest = list.first()
                    netValueInfo.postValue(Triple(latest.DWJZ, latest.LJJZ, latest.FSRQ))
                }
            }
        } catch (e: Exception) {
            LogUtils.e("获取历史净值失败", e)
        }
    }

    private fun parseHistoryNetValueFromHtml(html: String): List<FundHistoryNetValue> {
        val list = mutableListOf<FundHistoryNetValue>()
        try {
            val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
            val rowMatcher = rowPattern.matcher(html)
            while (rowMatcher.find()) {
                val row = rowMatcher.group(1) ?: continue
                val cellPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL)
                val cellMatcher = cellPattern.matcher(row)
                val cells = mutableListOf<String>()
                while (cellMatcher.find()) {
                    var cell = cellMatcher.group(1) ?: ""
                    cell = cell.replace(Regex("<[^>]+>"), "").trim()
                    cells.add(cell)
                }
                if (cells.size >= 4 && cells[0].matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    list.add(FundHistoryNetValue(
                        FSRQ = cells[0],
                        DWJZ = cells[1],
                        LJJZ = cells[2],
                        JZZZL = cells[3]
                    ))
                }
            }
        } catch (e: Exception) {
            LogUtils.e("解析历史净值HTML失败", e)
        }
        return list
    }

    // ---- 业绩走势 + 历史业绩 (同一个 pingzhongdata 接口) ----
    private suspend fun loadPerformanceTrendAndPerformance(fundCode: String) {
        try {
            val response = RetrofitClient.api.getFundPerformanceTrend(fundCode).await()
            val js = response.string()

            // 1. 解析 Data_netWorthTrend → 完整净值走势点（含时间戳）
            val trendList = parseTrendPoints(js)

            // 2. 解析阶段收益率（传入走势数据用于计算3年/5年/成立以来）
            parsePerformance(js, trendList)

        } catch (e: Exception) {
            LogUtils.e("获取业绩走势失败", e)
        }
    }

    /**
     * 从 pingzhongdata.js 解析净值走势数组
     * 格式: Data_netWorthTrend = [{x:时间戳毫秒, y:净值, equityReturn:十万份收益, unitMoney:""},…]
     * @return 排序后的走势列表，同时通过 allTrendPoints postValue
     */
    private fun parseTrendPoints(js: String): List<FundTrendPoint> {
        try {
            val pattern = Pattern.compile(
                "Data_netWorthTrend\\s*=\\s*(\\[.*?\\])\\s*;",
                Pattern.DOTALL
            )
            val matcher = pattern.matcher(js)
            if (!matcher.find()) return emptyList()

            val jsonArray = JSONArray(matcher.group(1))
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val list = mutableListOf<FundTrendPoint>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val ts = obj.optLong("x", 0L)
                val nv = obj.optDouble("y", 0.0).toFloat()
                if (ts > 0 && nv > 0) {
                    list.add(FundTrendPoint(date = sdf.format(Date(ts)), netValue = nv, timestamp = ts))
                }
            }

            allTrendPoints.postValue(list)
            return list
        } catch (e: Exception) {
            LogUtils.e("解析走势数据失败", e)
            return emptyList()
        }
    }

    /**
     * 从 pingzhongdata.js 解析阶段收益率
     * 实际字段（注意：字段后缀与含义不对应，以JS注释为准）：
     *   syl_1y  = 近1月
     *   syl_3y  = 近3月
     *   syl_6y  = 近6月
     *   syl_1n  = 近1年
     * 3年/5年/成立以来：通过走势数据计算
     */
    private fun parsePerformance(js: String, trendList: List<FundTrendPoint>) {
        try {
            // pingzhongdata.js 中只有4个固定字段（含义从注释确认）
            val fixedKeys = listOf(
                "syl_1y" to "近1月",
                "syl_3y" to "近3月",
                "syl_6y" to "近6月",
                "syl_1n" to "近1年"
            )
            val list = mutableListOf<FundPerformance>()
            for ((key, label) in fixedKeys) {
                val pattern = Pattern.compile("$key\\s*=\\s*\"?(-?[\\d.]+)\"?\\s*;")
                val matcher = pattern.matcher(js)
                val value = if (matcher.find()) {
                    val v = matcher.group(1).toFloatOrNull()
                    if (v != null) formatReturnRate(v) else "--"
                } else {
                    "--"
                }
                list.add(FundPerformance(period = label, periodKey = key, fundReturn = value))
            }

            // 3年/5年/成立以来：从走势数据中计算累计收益率
            val extraPeriods = listOf(
                Triple("3y",  "近3年",  1095),
                Triple("5y",  "近5年",  1825),
                Triple("ls",  "成立以来", Int.MAX_VALUE)
            )
            for ((key, label, days) in extraPeriods) {
                val filtered = getTrendByPeriod(key, trendList)
                val value = if (filtered.size >= 2) {
                    val base = filtered.first().netValue
                    val last = filtered.last().netValue
                    if (base > 0) formatReturnRate((last - base) / base * 100f) else "--"
                } else "--"
                list.add(FundPerformance(period = label, periodKey = key, fundReturn = value))
            }

            performances.postValue(list)
        } catch (e: Exception) {
            LogUtils.e("解析历史业绩失败", e)
        }
    }

    private fun formatReturnRate(v: Float): String {
        return if (v >= 0) String.format("+%.2f%%", v) else String.format("%.2f%%", v)
    }

    // ---- 基金持仓 ----
    private suspend fun loadHoldings(fundCode: String) {
        try {
            val response = RetrofitClient.api.getFundHoldings(code = fundCode).await()
            val jsonStr = response.string()

            val pattern = Pattern.compile("var apidata\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL)
            val matcher = pattern.matcher(jsonStr)
            if (matcher.find()) {
                val jsonObject = JSONObject(matcher.group(1))
                val content = jsonObject.optString("content", "")
                val list = parseHoldingsFromHtml(content)
                holdings.postValue(list)
            }
        } catch (e: Exception) {
            LogUtils.e("获取基金持仓失败", e)
        }
    }

    private fun parseHoldingsFromHtml(html: String): List<FundHolding> {
        val list = mutableListOf<FundHolding>()
        val seenCodes = mutableSetOf<String>()
        try {
            val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
            val rowMatcher = rowPattern.matcher(html)
            while (rowMatcher.find()) {
                val row = rowMatcher.group(1) ?: continue
                if (row.contains("股票代码") || row.contains("序号")) continue

                // 提取6位股票代码
                val codeMatch = Regex("([0-9]{6})").find(row)
                val code = codeMatch?.value ?: continue
                if (seenCodes.contains(code)) continue

                // 提取所有纯文本
                val texts = Regex(">([^<>]+)<").findAll(row)
                    .map { it.groupValues[1].replace("&nbsp;", "").trim() }
                    .filter { it.isNotEmpty() && it != "&nbsp;" }
                    .toList()

                // 提取名称（2-10个中文字符）
                val name = texts.firstOrNull { it.matches(Regex("[\\u4e00-\\u9fa5A-Za-z0-9]{2,12}")) &&
                        !it.matches(Regex("\\d+")) } ?: "--"

                // 提取持仓占比（带%，数值在0-100范围）
                val ratio = texts.firstOrNull { t ->
                    val clean = t.replace("%", "")
                    val f = clean.toFloatOrNull()
                    f != null && f in 0f..100f && t.contains("%")
                } ?: "--"

                // 提取较上期变动（含+/-或"增"/"减"或第二个%值）
                val change = texts.firstOrNull { t ->
                    t != ratio && (t.startsWith("+") || t.startsWith("-") ||
                            t.contains("增") || t.contains("减") ||
                            (t.contains("%") && t != ratio))
                } ?: "--"

                seenCodes.add(code)
                list.add(FundHolding(
                    GPDM = code,
                    GPJC = name,
                    JZBL = formatPercent(ratio),
                    PCTNVCHG = formatChange(change)
                ))
            }
        } catch (e: Exception) {
            LogUtils.e("解析持仓HTML失败", e)
        }
        return list
    }

    private fun formatPercent(value: String): String {
        val num = value.replace("%", "").trim().toFloatOrNull()
        return if (num != null) String.format("%.2f%%", num) else value
    }

    private fun formatChange(change: String): String {
        val c = change.trim()
        return when {
            c.contains("增") -> {
                val v = c.replace(Regex("[^0-9.]"), "")
                if (v.isNotEmpty()) "+$v%" else c
            }
            c.contains("减") -> {
                val v = c.replace(Regex("[^0-9.]"), "")
                if (v.isNotEmpty()) "-$v%" else c
            }
            c.startsWith("+") || c.startsWith("-") -> {
                val num = c.replace("%", "").toFloatOrNull()
                if (num != null) String.format("%+.2f%%", num) else c
            }
            c.replace("%", "").toFloatOrNull()?.let { it > 0 } == true ->
                String.format("+%.2f%%", c.replace("%", "").toFloat())
            c.replace("%", "").toFloatOrNull()?.let { it < 0 } == true ->
                String.format("%.2f%%", c.replace("%", "").toFloat())
            else -> c
        }
    }

    /**
     * 根据选定时间段截取走势数据
     * @param periodKey 时间段key ("1m","3m","6m","1y","3y","5y","ls")
     */
    fun getTrendByPeriod(periodKey: String, full: List<FundTrendPoint>): List<FundTrendPoint> {
        if (full.isEmpty()) return full
        if (periodKey == "ls") return full

        val days = when (periodKey) {
            "1m" -> 30
            "3m" -> 90
            "6m" -> 180
            "1y" -> 365
            "3y" -> 1095
            "5y" -> 1825
            else -> return full
        }

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val cutoff = cal.timeInMillis

        return full.filter { it.timestamp >= cutoff }
    }
}
