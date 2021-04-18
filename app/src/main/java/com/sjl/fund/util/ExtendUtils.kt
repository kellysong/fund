package com.sjl.fund.util

import android.text.TextUtils

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename ExtendUtils
 * @time 2021/4/13 15:07
 * @copyright(C) 2021 song
 */


/**
 * 过滤分割后为空的元素
 * @receiver String
 * @param delimiters String
 * @return MutableList<String>
 */
fun String.splitNotNull(delimiters:String):MutableList<String> {
    val tempList:MutableList<String> = mutableListOf()
    val split = split(delimiters)
    for (s in split){
        if (TextUtils.isEmpty(s)){
            continue
        }
        tempList.add(s)
    }
    return tempList
}
