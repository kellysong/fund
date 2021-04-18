package com.sjl.fund.util

import java.util.*

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename DateUtils
 * @time 2021/4/13 17:25
 * @copyright(C) 2021 song
 */
object DateUtils {

    /**
     * 当前时间是否处于某个一个时间段内
     * @param nowTime Date
     * @param beginTime Date
     * @param endTime Date
     * @return Boolean
     */
    fun belongCalendar(nowTime: Date, beginTime: Date, endTime: Date): Boolean {
        val date = Calendar.getInstance()
        date.time = nowTime
        //设置开始时间
        val begin = Calendar.getInstance()
        begin.time = beginTime
        //设置结束时间
        val end = Calendar.getInstance()
        end.time = endTime;
        //处于开始时间之后，和结束时间之前的判断
        return date.after(begin) && date.before(end)
    }

}
