package com.sjl.fund.util

import java.math.BigDecimal

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename MoneyUtils
 * @time 2021/6/30 11:14
 * @copyright(C) 2021 song
 */
object MoneyUtils {

    fun formatMoney(money: String, decimals: Int):String {
        var  bigDecimal = BigDecimal(money)
        bigDecimal= bigDecimal.setScale(2, BigDecimal.ROUND_HALF_UP);//保留两位小数
        return bigDecimal.toString()
    }

    fun formatMoney(money: Double, decimals: Int):String {
        return formatMoney(money.toString(),decimals)
    }
}