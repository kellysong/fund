package com.sjl.fund.data

import com.sjl.fund.entity.FundInfo

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename FundDataSource
 * @time 2021/4/16 11:23
 * @copyright(C) 2021 song
 */

interface FundDataSource {
    /**
     *
     * @return MutableList<String>?
     */
    fun listFundCodeList(): MutableList<String>?

    /**
     *
     * @param fundCode String
     */
    fun deleteFund(fundCode: String)

    /**
     *
     * @param data MutableList<FundInfo>
     */
    fun sortFund(data: MutableList<FundInfo>)

    /**
     *
     * @param fundInfo FundInfo
     */
    fun insertFund(fundInfo: FundInfo)

    /**
     *
     * @return Int
     */
    fun getMaxSortId(): Int
}
