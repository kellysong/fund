package com.sjl.fund.entity

import androidx.room.Entity
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
        var sortId: Int
)

