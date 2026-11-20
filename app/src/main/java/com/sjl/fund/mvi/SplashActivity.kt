package com.sjl.fund.mvi

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 启动页：不设布局，启动图由 AppTheme.Launcher 的 windowBackground 绘制，
 * 最短展示 MIN_SPLASH_DISPLAY_MILLIS 后跳转主页。
 */
class SplashActivity : AppCompatActivity() {

    private val splashEnterTime = SystemClock.uptimeMillis()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 主题已设置 windowBackground，无需 setContentView
        val remaining = MIN_SPLASH_DISPLAY_MILLIS - (SystemClock.uptimeMillis() - splashEnterTime)
        if (remaining > 0) {
            mainHandler.postDelayed({ doOpenMain() }, remaining)
        } else {
            doOpenMain()
        }
    }

    private fun doOpenMain() {
        startActivity(Intent(this, FundListActivity2::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /**
     * 基类/主题在 onResume、焦点变化时会重置状态栏，这里再覆盖一次，
     * 保持与 windowBackground 同色（白色 + 深色图标），避免启动瞬间闪色。
     */
    override fun onResume() {
        super.onResume()
        initStatusColor()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) initStatusColor()
    }

    private fun initStatusColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val MIN_SPLASH_DISPLAY_MILLIS = 350L
    }
}
