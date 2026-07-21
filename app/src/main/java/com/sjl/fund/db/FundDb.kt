package com.sjl.fund.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sjl.core.util.ViewUtils
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
], version = 5)
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
                .addMigrations(MIGRATION_1_TO_2, MIGRATION_2_TO_3, MIGRATION_3_TO_4, MIGRATION_4_TO_5)
                .allowMainThreadQueries() // 允许主线程执行SQL语句
                .build()
        //https://www.jianshu.com/p/41272f319ae7?utm_campaign=maleskine
        val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table fund_info add column holdFlag INTEGER NOT NULL DEFAULT 1")
                database.execSQL("alter table fund_info add column holdMoney real NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_TO_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table fund_info add column fundType INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_TO_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table fund_info add column createTime INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_TO_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("alter table fund_info add column jzzzl TEXT NOT NULL DEFAULT ''")
            }
        }
    }

}