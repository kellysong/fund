package com.sjl.fund.data

import com.sjl.core.kotlin.util.PreferencesHelper
import com.sjl.core.kotlin.util.ViewUtils
import com.sjl.fund.entity.FundInfo
import com.sjl.fund.util.splitNotNull

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename FundFromSp
 * @time 2021/4/16 11:24
 * @copyright(C) 2021 song
 */
class FundFromSp : FundDataSource {
    val sp = PreferencesHelper.getInstance(ViewUtils.getContext())

    val fundCodeTAG = "fundCodeTAG"

    override fun listFundCodeList(): MutableList<String> {
        val oldFundCodeList = sp.get(fundCodeTAG, "") as String
        return oldFundCodeList.splitNotNull(",")
    }

    override fun listFundInfos(): MutableList<FundInfo>? {
        return null
    }


    override fun deleteFund(fundCode: String) {
        val split = listFundCodeList()
        val sb = StringBuffer()
        for (s in split!!) {
            if (s != fundCode) {
                sb.append("${s},")
            }
        }
        saveFund(sb)

    }

    override fun sortFund(data: MutableList<FundInfo>) {
        val sb = StringBuffer()
        data.forEach {
            sb.append("${it.fundcode},")
        }
        saveFund(sb)
    }
    private fun saveFund(sb: StringBuffer) {
        if (sb.isNotEmpty()) {
            val deleteCharAt = sb.deleteCharAt(sb.length - 1)
            sp.put(fundCodeTAG, deleteCharAt.toString())
        } else {
            sp.put(fundCodeTAG, sb.toString())
        }
    }

    override fun insertFund(fundInfo: FundInfo) {
        val s = sp.get(fundCodeTAG, "") as String
        if (s.isNullOrEmpty()){
            sp.put(fundCodeTAG, "${fundInfo.fundcode}")
        }else{
            if (s.contains(fundInfo.fundcode)) {
                return
            }
            sp.put(fundCodeTAG, "$s,${fundInfo.fundcode}")
        }
    }

    /**
     * sp下不支持
     * @return Int
     */
    override fun getMaxSortId(): Int {
       return 0
    }

}