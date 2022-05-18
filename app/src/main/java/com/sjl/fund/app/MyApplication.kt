package com.sjl.fund.app

import android.content.Context
import androidx.multidex.MultiDex
import com.sjl.core.app.BaseApplication
import com.sjl.core.net.BaseUrlAdapter
import com.sjl.core.net.RetrofitHelper
import com.sjl.core.net.RetrofitLogAdapter
import com.sjl.core.net.RetrofitParams
import com.sjl.fund.BuildConfig
import com.sjl.fund.db.FundDb

class MyApplication : BaseApplication() {
    override fun onCreate() {
        super.onCreate()
        initLogConfig(BuildConfig.DEBUG)
        initRetrofitClient()
    }

    private fun initRetrofitClient() {
        RetrofitHelper.getInstance().init(RetrofitParams.Builder().setBaseUrlAdapter(object : BaseUrlAdapter {
            override fun getDefaultBaseUrl(): String {
                return "http://fundgz.1234567.com.cn"
            }

            override fun getAppendBaseUrl(): MutableMap<String, String>? {
                return null
            }
        }).setRetrofitLogAdapter(object : RetrofitLogAdapter{
            override fun printRequestUrl(): Boolean {
                return true
            }

            override fun printHttpLog(): Boolean {
                return false
            }

        }).setUseCoroutines(true).build())
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}