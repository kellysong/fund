package com.sjl.fund.db.dao

import androidx.room.*
import com.sjl.fund.entity.FundInfo

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename FundInfoDao
 * @time 2021/4/15 17:55
 * @copyright(C) 2021 song
 */
@Dao
interface FundInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(data: FundInfo?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(data: MutableList<FundInfo>?)

    @Query("delete FROM fund_info WHERE fundcode = :fundcode")
    fun delete(fundcode: String?)

    @Query("SELECT * FROM fund_info WHERE fundcode = :fundcode")
    fun getFundInfo(fundcode: String?): FundInfo?

    @Query("SELECT * FROM fund_info")
    fun listFundInfos(): MutableList<FundInfo>?


    @Query("SELECT fundcode FROM fund_info")
    fun listFundCode(): MutableList<String>?

}