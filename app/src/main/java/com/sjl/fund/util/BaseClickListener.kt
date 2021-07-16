package com.sjl.fund.util

import android.os.Handler
import android.os.Looper
import android.view.View


abstract class BaseClickListener protected constructor() : View.OnClickListener {
    private var clickCount = 0
    private val handler: Handler
    val TIMEOUT:Long = 400

    override fun onClick(v: View?) {
        clickCount++
        handler.postDelayed(Runnable {
            if (clickCount == 1) {
                onSingleClick(v)
            } else if (clickCount == 2) {
                onDoubleClick(v)
            }
            handler.removeCallbacksAndMessages(null)
            clickCount = 0
        }, TIMEOUT)
    }

    /**
     * 单击实现
     *
     * @param v 视图
     */
    abstract fun onSingleClick(v: View?)

    /**
     * 双击实现
     *
     * @param v 视图
     */
    abstract fun onDoubleClick(v: View?)

    companion object {
        private const val TIMEOUT = 400
    }

    init {
        handler =  Handler(Looper.getMainLooper())
    }
}

