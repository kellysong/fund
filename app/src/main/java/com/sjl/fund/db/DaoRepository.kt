package com.sjl.fund.db

import com.sjl.fund.entity.FundInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename DaoRepository
 * @time 2021/4/15 18:05
 * @copyright(C) 2021 song
 */
object DaoRepository {
    private val dao = FundDb.getInstance().userLoginDao

     fun insert(data: FundInfo?) {
        dao.insert(data)
    }

    fun insertList(data: MutableList<FundInfo>?){
        dao.insertList(data)
    }
    fun listFundInfos(): MutableList<FundInfo>? {
        val listFundInfos = dao.listFundInfos()
        return listFundInfos
    }

    fun listFundCode(): MutableList<String>? {
        val fundCodes = dao.listFundCode()
        return fundCodes
    }

    fun delete(fundcode: String) {
        dao.delete(fundcode)
    }
}