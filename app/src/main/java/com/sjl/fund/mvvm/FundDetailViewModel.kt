package com.sjl.fund.mvvm

import androidx.lifecycle.MutableLiveData
import com.sjl.core.mvvm.BaseViewModel
import com.sjl.core.util.log.LogUtils
import com.sjl.fund.entity.FundHistoryNetValue
import com.sjl.fund.entity.FundHolding
import com.sjl.fund.entity.FundBondHolding
import com.sjl.fund.entity.FundPerformance
import com.sjl.fund.entity.FundTrendPoint
import com.sjl.fund.entity.AssetAllocationSlice
import com.sjl.fund.net.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.await
import java.io.BufferedReader
import java.io.InputStreamReader
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
    val bondHoldings = MutableLiveData<List<FundBondHolding>>()
    val assetAllocation = MutableLiveData<List<AssetAllocationSlice>>()
    val netValueInfo = MutableLiveData<Triple<String, String, String>>() // 单位净值/累计净值/日期
    val realTimeValue = MutableLiveData<Triple<String, String, String>>() // 估值/涨跌幅/估值时间
    val fundBaseInfo = MutableLiveData<Triple<String, String, String>>() // 类型/公司/经理
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
                    loadFundBaseInfo(fundCode)          // 类型/公司/经理
                    loadHistoryNetValue(fundCode)       // 头部 + 历史净值表 (lsjz)
                    loadRealTimeValue(fundCode)         // 涨跌幅 (fundgz)
                    loadPerformanceTrendAndPerformance(fundCode)
                    loadHoldings(fundCode)
                    loadBondHoldings(fundCode)
                    loadAssetAllocation(fundCode)
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
     * 加载盘中实时估值（替代已下架的 getFundInfo / fundgz 接口，改用新浪行情接口 fu_）
     */
    private suspend fun loadRealTimeValue(fundCode: String) {
        try {
            val response = RetrofitClient.api.getFundInfoV2(fundCode).await()
            val text = response.string()
            val start = text.indexOf("\"")
            val end = text.lastIndexOf("\"")
            if (start >= 0 && end > start) {
                val parts = text.substring(start + 1, end).split(",")
                if (parts.size >= 8) {
                    val gsz = parts[2]
                    val gszzl = try {
                        // 盘中估值涨跌幅保留两位小数
                        String.format("%.2f", parts[6].toDouble())
                    } catch (e: Exception) {
                        parts[6]
                    }
                    val gztime = parts[1]
                    realTimeValue.postValue(Triple(gsz, gszzl, gztime))
                }
            }
        } catch (e: Exception) {
            LogUtils.e("获取盘中估值失败", e)
        }
    }

    // ---- 基金基本信息 ----
    private suspend fun loadFundBaseInfo(fundCode: String) {
        try {
            val resp = RetrofitClient.api.getFundNameByCode(key = fundCode).await()
            val root = JSONObject(resp.string())
            val datas = root.optJSONArray("Datas") ?: return
            if (datas.length() == 0) return
            val info = datas.getJSONObject(0).optJSONObject("FundBaseInfo") ?: return
            val type = info.optString("FTYPE", "")
            val company = info.optString("JJGS", "")
            val manager = info.optString("JJJL", "")
            fundBaseInfo.postValue(Triple(type, company, manager))
        } catch (e: Exception) {
            LogUtils.e("获取基金基本信息失败", e)
        }
    }

    // ---- 历史净值 ----
    private suspend fun loadHistoryNetValue(fundCode: String) {
        try {
            val response = RetrofitClient.api.getFundHistoryNetValue(
                fundCode = fundCode,
                pageSize = 30
            ).await()
            val jsonStr = response.string()

            val list = parseHistoryNetValueFromJson(jsonStr)
            historyNetValues.postValue(list)
            if (list.isNotEmpty()) {
                val latest = list.first()
                netValueInfo.postValue(Triple(latest.DWJZ, latest.LJJZ, latest.FSRQ))
            }
        } catch (e: Exception) {
            LogUtils.e("获取历史净值失败", e)
        }
    }

    /**
     * 解析东方财富历史净值 JSON 接口
     * 返回结构：{"Data":{"LSJZList":[{"FSRQ","DWJZ","LJJZ","JZZZL",...}]},"Success":true,...}
     * LSJZList 默认按日期降序（最新在前）
     */
    private fun parseHistoryNetValueFromJson(json: String): List<FundHistoryNetValue> {
        val list = mutableListOf<FundHistoryNetValue>()
        try {
            val root = JSONObject(json)
            val data = root.optJSONObject("Data") ?: return emptyList()
            val lsjz = data.optJSONArray("LSJZList") ?: return emptyList()
            for (i in 0 until lsjz.length()) {
                val obj = lsjz.getJSONObject(i)
                list.add(FundHistoryNetValue(
                    FSRQ = obj.optString("FSRQ", ""),
                    DWJZ = obj.optString("DWJZ", "--"),
                    LJJZ = obj.optString("LJJZ", "--"),
                    JZZZL = obj.optString("JZZZL", "--")
                ))
            }
        } catch (e: Exception) {
            LogUtils.e("解析历史净值JSON失败", e)
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

    // ---- 基金持仓（重仓股票） ----
    // 注意：getFundHoldings 不传 year/month 时，部分基金返回空数据，
    // 必须按最新报告期（最近的季度末）请求才能拿到当前持仓。
    private suspend fun loadHoldings(fundCode: String) {
        try {
            var stockList: List<FundHolding>? = null
            for ((y, m) in latestReportPeriods()) {
                val response = RetrofitClient.api.getFundHoldings(
                    code = fundCode, year = y, month = m
                ).await()
                val jsonStr = response.string()
                val pattern = Pattern.compile("var apidata\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL)
                val matcher = pattern.matcher(jsonStr)
                if (matcher.find()) {
                    val jsonObject = JSONObject(matcher.group(1))
                    val content = jsonObject.optString("content", "")
                    val list = parseHoldingsFromHtml(content)
                    if (list.isNotEmpty()) {
                        // 获取持仓股票的当日涨跌（新浪接口计算涨跌幅）
                        fetchStockDailyChanges(list)
                        // 计算较上期变动（与上一报告期占净值比例对比）
                        fetchPreviousQuarterRatio(fundCode, content, list)
                        stockList = list
                        break
                    }
                }
            }
            stockList?.let { holdings.postValue(it) }
        } catch (e: Exception) {
            LogUtils.e("获取基金持仓失败", e)
        }
    }

    // ---- 重仓债券 ----
    private suspend fun loadBondHoldings(fundCode: String) {
        try {
            var bondList: List<FundBondHolding>? = null
            var bondContent: String? = null
            for ((y, m) in latestReportPeriods()) {
                val response = RetrofitClient.api.getFundHoldings(
                    code = fundCode, type = "zqcc", year = y, month = m
                ).await()
                val jsonStr = response.string()
                val pattern = Pattern.compile("var apidata\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL)
                val matcher = pattern.matcher(jsonStr)
                if (matcher.find()) {
                    val content = JSONObject(matcher.group(1)).optString("content", "")
                    val list = parseBondHoldingsFromHtml(content)
                    if (list.isNotEmpty()) {
                        bondList = list
                        bondContent = content
                        break
                    }
                }
            }
            bondList?.let { list ->
                // 计算「较上期」变动（对比上一报告期占净值比例）
                fetchPreviousBondQuarterRatio(fundCode, bondContent ?: "", list)
                bondHoldings.postValue(list)
            }
        } catch (e: Exception) {
            LogUtils.e("获取债券持仓失败", e)
        }
    }

    /**
     * 计算债券持仓「较上期」变动：当前报告期占净值比例 - 上一报告期占净值比例
     */
    private suspend fun fetchPreviousBondQuarterRatio(
        fundCode: String,
        content: String,
        holdings: List<FundBondHolding>
    ) {
        if (holdings.isEmpty()) return
        try {
            val cutoff = parseCutoffDate(content) ?: return
            val (prevYear, prevMonth) = previousQuarterEnd(cutoff)
            val resp = RetrofitClient.api.getFundHoldings(
                code = fundCode, type = "zqcc", year = prevYear, month = prevMonth
            ).await()
            val prevContent = JSONObject(
                Regex("var apidata\\s*=\\s*(\\{.*?\\});", RegexOption.DOT_MATCHES_ALL)
                    .find(resp.string())?.groupValues?.getOrNull(1) ?: return
            ).optString("content", "")
            val prevRatios = parseBondRatioMap(prevContent)

            for (holding in holdings) {
                val cur = holding.JZBL.replace("%", "").toFloatOrNull() ?: continue
                val prev = prevRatios[holding.ZQDM]
                holding.PCTNVCHG = if (prev == null) {
                    "新进"
                } else {
                    formatChangePercent(cur - prev)
                }
            }
        } catch (e: Exception) {
            LogUtils.e("获取债券上期持仓失败", e)
        }
    }

    /**
     * 从债券持仓 HTML 中解析「债券代码 -> 占净值比例」映射
     */
    private fun parseBondRatioMap(html: String): Map<String, Float> {
        val map = mutableMapOf<String, Float>()
        try {
            val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
            val rowMatcher = rowPattern.matcher(html)
            while (rowMatcher.find()) {
                val row = rowMatcher.group(1) ?: continue
                if (row.contains("债券代码") || row.contains("序号")) continue
                val tds = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(row)
                    .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", "").trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                if (tds.size < 4) continue
                val code = tds[1]
                val ratioText = tds[3]
                val f = ratioText.replace("%", "").toFloatOrNull()
                if (code.isNotBlank() && f != null) map[code] = f
            }
        } catch (e: Exception) {
            LogUtils.e("解析债券上期比例失败", e)
        }
        return map
    }

    // ---- 资产配置（股票/债券/现金/其它） ----
    private suspend fun loadAssetAllocation(fundCode: String) {
        try {
            val response = RetrofitClient.api.getAssetAllocation(fundCode).await()
            val html = response.string()
            val slices = parseAssetAllocation(html)
            assetAllocation.postValue(slices)
        } catch (e: Exception) {
            LogUtils.e("获取资产配置失败", e)
        }
    }

    /**
     * 生成最近的若干报告期(year, month)，按时间倒序，
     * 用于兼容部分基金不传 year/month 时返回空数据的情况。
     * 取当前日期往前推一个季度作为起点（留出披露延迟），再往前兜底3个季度。
     */
    private fun latestReportPeriods(): List<Pair<Int, Int>> {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val (baseY, baseM) = when {
            m >= 4 -> y to (if (m >= 10) 9 else if (m >= 7) 6 else 3)
            else -> (y - 1) to 12
        }
        val result = mutableListOf(baseY to baseM)
        var cy = baseY
        var cm = baseM
        repeat(3) {
            val pm = if (cm == 3) 12 else cm - 3
            val py = if (cm == 3) cy - 1 else cy
            result.add(py to pm)
            cy = py
            cm = pm
        }
        return result
    }

    /**
     * 为持仓股票获取当日涨跌（涨跌幅）
     * 新浪返回格式：var hq_str_sh688486="名称,今开,昨收,当前价,最高,最低,..."
     * 用「当前价 - 昨收」/ 昨收 计算涨跌幅，避免直接取涨跌额字段（该字段已偏移/不可靠）
     */
    private suspend fun fetchStockDailyChanges(holdings: List<FundHolding>) {
        if (holdings.isEmpty()) return
        try {
            val stockCodes = holdings.mapNotNull { holding ->
                val code = holding.GPDM
                when {
                    code.startsWith("6") -> "sh$code"  // 上海
                    code.startsWith("0") || code.startsWith("3") -> "sz$code"  // 深圳
                    else -> null
                }
            }
            if (stockCodes.isEmpty()) return

            val url = "http://hq.sinajs.cn/list=${stockCodes.joinToString(",")}"
            val response = RetrofitClient.api.getSinaQuotes(url).await()
            val reader = BufferedReader(InputStreamReader(response.byteStream(), "GBK"))
            val rawData = reader.readText()
            reader.close()

            // 解析股票数据：var hq_str_sh601398="名称,今开,昨收,当前价,..."
            val pattern = Regex("""var hq_str_(\w+)="([^"]+)"""")
            val changeMap = mutableMapOf<String, String>()
            for (match in pattern.findAll(rawData)) {
                val prefixCode = match.groupValues[1]  // sh601398
                val fields = match.groupValues[2].split(",")
                val prevClose = fields.getOrNull(2)?.toFloatOrNull()  // 昨收
                val curPrice = fields.getOrNull(3)?.toFloatOrNull()   // 当前价
                if (prevClose != null && prevClose > 0 && curPrice != null) {
                    val pct = (curPrice - prevClose) / prevClose * 100f
                    changeMap[prefixCode] = formatChangePercent(pct)
                }
            }

            // 更新持仓数据
            for (holding in holdings) {
                val prefix = when {
                    holding.GPDM.startsWith("6") -> "sh"
                    holding.GPDM.startsWith("0") || holding.GPDM.startsWith("3") -> "sz"
                    else -> null
                }
                val key = "$prefix${holding.GPDM}"
                changeMap[key]?.let { holding.dailyChange = it }
            }
        } catch (e: Exception) {
            LogUtils.e("获取股票行情失败", e)
        }
    }

    /**
     * 计算持仓「较上期」变动：当前报告期占净值比例 - 上一报告期占净值比例
     * jjcc 接口本身不含「较上期」列，需取上一报告期持仓对比得出
     */
    private suspend fun fetchPreviousQuarterRatio(
        fundCode: String,
        content: String,
        holdings: List<FundHolding>
    ) {
        if (holdings.isEmpty()) return
        try {
            val cutoff = parseCutoffDate(content) ?: return
            val (prevYear, prevMonth) = previousQuarterEnd(cutoff)
            val resp = RetrofitClient.api.getFundHoldings(
                code = fundCode, year = prevYear, month = prevMonth
            ).await()
            val prevRatios = parseRatioMap(resp.string())

            for (holding in holdings) {
                val cur = holding.JZBL.replace("%", "").toFloatOrNull() ?: continue
                val prev = prevRatios[holding.GPDM]
                holding.PCTNVCHG = if (prev == null) {
                    "新进"   // 上期未持有
                } else {
                    formatChangePercent(cur - prev)
                }
            }
        } catch (e: Exception) {
            LogUtils.e("获取上期持仓失败", e)
        }
    }

    /**
     * 从持仓 HTML 中解析报告截止日期（如 2026-03-31）
     */
    private fun parseCutoffDate(content: String): String? {
        val m = Regex("""截止至：<font[^>]*>(\d{4}-\d{2}-\d{2})""").find(content)
        return m?.groupValues?.getOrNull(1)
    }

    /**
     * 根据截止日期计算上一报告期（季度）的 year/month
     * 例：2026-03-31 -> (2025,12)；2026-06-30 -> (2026,3)
     */
    private fun previousQuarterEnd(cutoff: String): Pair<Int, Int> {
        val parts = cutoff.split("-")
        if (parts.size < 2) return Pair(0, 0)
        val y = parts[0].toIntOrNull() ?: return Pair(0, 0)
        val m = parts[1].toIntOrNull() ?: return Pair(0, 0)
        val q = (m - 1) / 3  // 0..3
        return if (q == 0) Pair(y - 1, 12) else Pair(y, q * 3)
    }

    /**
     * 从 jjcc 接口返回的 JSON 中解析「股票代码 -> 占净值比例」映射
     */
    private fun parseRatioMap(jsonStr: String): Map<String, Float> {
        val map = mutableMapOf<String, Float>()
        val apidataMatch = Regex("""var apidata\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(jsonStr)
            ?: return map
        val content = JSONObject(apidataMatch.groupValues[1]).optString("content", "")
        val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
        val rowMatcher = rowPattern.matcher(content)
        while (rowMatcher.find()) {
            val row = rowMatcher.group(1) ?: continue
            if (row.contains("股票代码") || row.contains("序号")) continue
            val codeMatch = Regex("([0-9]{6})").find(row) ?: continue
            val code = codeMatch.value
            val texts = Regex(">([^<>]+)<").findAll(row)
                .map { it.groupValues[1].replace("&nbsp;", "").trim() }
                .filter { it.isNotEmpty() && it != "&nbsp;" }
                .toList()
            val ratio = texts.firstOrNull { t ->
                val f = t.replace("%", "").toFloatOrNull()
                f != null && f in 0f..100f && t.contains("%")
            }
            ratio?.let {
                val f = it.replace("%", "").toFloatOrNull()
                if (f != null) map[code] = f
            }
        }
        return map
    }

    private fun formatChangePercent(pct: Float): String {
        return if (pct >= 0) String.format("+%.2f%%", pct) else String.format("%.2f%%", pct)
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

    /**
     * 解析债券持仓 HTML（type=zqcc）
     * 结构：序号 | 债券代码 | 债券名称 | 占净值比例 | 持仓市值（万元）
     * 债券代码可能为6或7位，故按 <td> 顺序提取，避免按固定6位正则截断。
     */
    private fun parseBondHoldingsFromHtml(html: String): List<FundBondHolding> {
        val list = mutableListOf<FundBondHolding>()
        try {
            val rowPattern = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL)
            val rowMatcher = rowPattern.matcher(html)
            while (rowMatcher.find()) {
                val row = rowMatcher.group(1) ?: continue
                if (row.contains("债券代码") || row.contains("序号")) continue // 跳过表头
                val tds = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL).findAll(row)
                    .map { it.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", "").trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                if (tds.size < 4) continue
                val code = tds[1]
                val name = tds[2]
                val ratio = tds[3]
                if (code.isBlank() || name.isBlank()) continue
                list.add(FundBondHolding(ZQDM = code, ZQMC = name, JZBL = ratio))
            }
        } catch (e: Exception) {
            LogUtils.e("解析债券持仓HTML失败", e)
        }
        return list
    }

    /**
     * 从资产配置页面 HTML 解析 var chartData（GP/ZQ/XJ/CTPZ 各季度数组），
     * 取最新一期作为饼图切片：股票/债券/现金/其它。
     */
    private fun parseAssetAllocation(html: String): List<AssetAllocationSlice> {
        return try {
            val m = Regex("var chartData\\s*=\\s*(\\{.*?\\});", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: return emptyList()
            val json = JSONObject(m.groupValues[1])
            val dates = json.optJSONArray("Dates") ?: return emptyList()
            val last = dates.length() - 1
            if (last < 0) return emptyList()
            val gp = (json.optJSONArray("GP")?.optDouble(last) ?: 0.0).toFloat()
            val zq = (json.optJSONArray("ZQ")?.optDouble(last) ?: 0.0).toFloat()
            val xj = (json.optJSONArray("XJ")?.optDouble(last) ?: 0.0).toFloat()
            // 「其它」= 100 - 股票 - 债券 - 现金（取非负，体现剩余占比）
            val ctpz = maxOf(0f, 100f - gp - zq - xj)
            listOf(
                AssetAllocationSlice("股票", gp),
                AssetAllocationSlice("债券", zq),
                AssetAllocationSlice("现金", xj),
                AssetAllocationSlice("其它", ctpz)
            )
        } catch (e: Exception) {
            LogUtils.e("解析资产配置失败", e)
            emptyList()
        }
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
