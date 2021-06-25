package com.sjl.fund.db

import android.provider.ContactsContract
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sjl.core.kotlin.app.BaseApplication
import com.sjl.core.kotlin.util.ViewUtils
import com.sjl.fund.app.MyApplication
import com.sjl.fund.db.dao.FundInfoDao
import com.sjl.fund.entity.FundInfo

/**
 * TODO
 * @author Kelly
 * @version 1.0.0
 * @filename FundDb
 * @time 2021/4/15 17:47
 * @copyright(C) 2021 song
 */

@Database(entities = [
    FundInfo::class
], version = 1)
abstract class FundDb : RoomDatabase() {

            abstract val userLoginDao: FundInfoDao




            companion object {

            @Volatile
            private var instance: FundDb? = null

            fun getInstance() = instance ?: synchronized(FundDb::class.java) {
                instance ?: buildDatabase().also { instance = it }
            }

        private fun buildDatabase(): FundDb = Room.databaseBuilder(ViewUtils.getContext()
                , FundDb::class.java, "fund.db")
                .addMigrations()
                .allowMainThreadQueries()
                .build()

    }


}