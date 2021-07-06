package com.sjl.fund.db

import androidx.room.TypeConverter
import java.math.BigDecimal

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename BigDecimalConverter
 * @time 2021/6/29 15:59
 * @copyright(C) 2021 song
 */
class BigDecimalConverter {
    @TypeConverter
    fun fromString(value: String): BigDecimal? {
        return if (value.isEmpty()) {
            null
        } else {
            BigDecimal(value)
        }
    }

    @TypeConverter
    fun toString(bigDecimal: BigDecimal): String {
        return bigDecimal.toString()
    }

}