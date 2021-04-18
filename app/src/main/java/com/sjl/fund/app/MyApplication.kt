package com.sjl.fund.app

import android.content.Context
import androidx.multidex.MultiDex
import com.sjl.core.kotlin.app.BaseApplication
import com.sjl.core.kotlin.net.BaseUrlAdapter
import com.sjl.core.kotlin.net.RetrofitHelper
import com.sjl.core.kotlin.net.RetrofitParams
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
                return "https://wanandroid.com"
            }

            override fun getAppendBaseUrl(): MutableMap<String, String>? {
                return null
            }
        }).setUseCoroutines(true).build())
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}