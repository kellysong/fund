package com.sjl.fund.data

import com.sjl.fund.db.DaoRepository
import com.sjl.fund.entity.FundInfo

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename FundFromDb
 * @time 2021/4/16 11:23
 * @copyright(C) 2021 song
 */
class FundFromDb : FundDataSource {
    override fun listFundCodeList(): MutableList<String>? {
       return DaoRepository.listFundCode()
    }

    override fun listFundInfos(): MutableList<FundInfo>? {
        return DaoRepository.listFundInfos()
    }


    override fun deleteFund(fundCode: String) {
        DaoRepository.delete(fundCode)
    }

    override fun sortFund(data: MutableList<FundInfo>) {
        data.forEachIndexed{index: Int, fundInfo: FundInfo ->
            fundInfo.sortId = index;
        }
        DaoRepository.insertList(data)
    }

    override fun insertFund(fundInfo: FundInfo) {
        DaoRepository.insert(fundInfo)
    }

    override fun getMaxSortId(): Int {
        return DaoRepository.getMaxSortId()
    }

}