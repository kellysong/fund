package com.sjl.fund.entity

import android.graphics.Bitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * {"fundcode":"001970","name":"泰信鑫选灵活配置混合A","jzrq":"2021-04-09","dwjz":"1.1610","gsz":"1.1406","gszzl":"-1.76","gztime":"2021-04-12 15:00"}
 * @property dwjz String
 * @property fundcode String
 * @property gsz String
 * @property gszzl String
 * @property gztime String
 * @property jzrq String
 * @property name String
 * @constructor
 */
@Entity(tableName = "fund_info")
data class FundInfo(
        @PrimaryKey
        val fundcode: String,
        val dwjz: String,
        val gsz: String,
        val gszzl: String,
        val gztime: String,
        val jzrq: String,
        val name: String,
        var sortId: Int,
        /**
         * 0未持有，1持有
         */
        @ColumnInfo(defaultValue = "1")
        var holdFlag: Int,
        @ColumnInfo(defaultValue = "0")
        var holdMoney: Double,
        /**
         * 基金分类：0=自选，1=其他基金
         */
        @ColumnInfo(defaultValue = "0")
        var fundType: Int,
        /**
         * 添加时间戳，用于按添加时间降序排列
         */
        @ColumnInfo(defaultValue = "0")
        var createTime: Long = 0L,

){
    @Ignore  // 将忽略的字段放在类体内
    var operateType: Int = 0
}

/**
 * 基金历史净值数据
 * {"FSRQ":"2021-04-09","DWJZ":"1.1610","LJJZ":"1.1610","JZZZL":"-1.76"}
 */
data class FundHistoryNetValue(
    val FSRQ: String,  // 发生日期
    val DWJZ: String,  // 单位净值
    val LJJZ: String,  // 累计净值
    val JZZZL: String  // 净值增长率
)

/**
 * 基金业绩走势数据点
 */
data class FundTrendPoint(
    val date: String,       // 日期 yyyy-MM-dd
    val netValue: Float,    // 单位净值
    val timestamp: Long = 0L // 时间戳（毫秒），用于区间过滤
)

/**
 * 基金持仓信息
 */
data class FundHolding(
    val GPDM: String,      // 股票代码
    val GPJC: String,      // 股票简称
    val JZBL: String,      // 净值占比
    val PCTNVCHG: String,  // 持仓变动
    var dailyChange: String = "--",  // 当日涨跌（需要额外获取）
    var marketValue: String = "--"   // 持仓市值
)

/**
 * 基金历史业绩（阶段涨跌幅）
 * 数据来自 pingzhongdata.js 中的 syl_* 字段
 */
data class FundPerformance(
    val period: String,     // 时间段文字，如 "近1月"
    val periodKey: String,  // 时间段key，如 "1m"
    val fundReturn: String, // 基金涨跌幅，如 "+5.23%"
    val rank: String = "--" // 同类排名（预留）
)

/**
 * 基金详情数据（整合所有信息）
 */
data class FundDetail(
    val fundInfo: FundInfo,
    val historyNetValues: List<FundHistoryNetValue>,
    val trendPoints: List<FundTrendPoint>,
    val holdings: List<FundHolding>
)

/**
 * 指数行情数据
 * 上证指数: sh000001, 深证成指: sz399001, 创业板指: sz399006, 科创50: sh000688
 */
data class IndexData(
    val name: String,        // 指数名称，如"上证指数"
    val code: String,        // 指数代码
    val price: String,       // 当前点位
    val change: String,      // 涨跌额
    val changePercent: String // 涨跌幅
)

/**
 * 板块主力资金流向数据
 * 数据来源：天天基金/东方财富 push2.eastmoney.com 板块资金流接口
 * @property code 板块代码 (f12)
 * @property name 板块名称 (f14)
 * @property changePercent 涨跌幅(%) (f3)
 * @property mainNetInflow 主力净流入(元) (f62)
 * @property mainNetInflowPercent 主力净占比(%) (f184)
 * @property extraLargeNetInflow 超大单净流入(元) (f66)
 * @property largeNetInflow 大单净流入(元) (f72)
 * @property mediumNetInflow 中单净流入(元) (f78)
 * @property smallNetInflow 小单净流入(元) (f84)
 */
data class SectorFlowData(
    val code: String,
    val name: String,
    val changePercent: Double,
    val mainNetInflow: Double,
    val mainNetInflowPercent: Double,
    val extraLargeNetInflow: Double,
    val largeNetInflow: Double,
    val mediumNetInflow: Double,
    val smallNetInflow: Double
)

