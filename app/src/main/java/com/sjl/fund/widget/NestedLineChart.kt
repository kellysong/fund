package com.sjl.fund.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewParent
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener

/**
 * 解决 NestedScrollView 中 LineChart 横向滑动冲突
 * 当用户在图表上横向滑动时，请求父控件不拦截触摸事件
 */
class NestedLineChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LineChart(context, attrs, defStyle) {

    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private val touchSlop = 8 // dp，转换为像素

    private val slopPx: Int = (touchSlop * resources.displayMetrics.density).toInt()

    init {
        // 设置图表手势监听
        onChartGestureListener = object : OnChartGestureListener {
            override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                // 手势开始时请求父控件不拦截
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {
                // 手势结束后恢复
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            override fun onChartLongPressed(me: MotionEvent?) {}
            override fun onChartDoubleTapped(me: MotionEvent?) {}
            override fun onChartSingleTapped(me: MotionEvent?) {}
            override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
                // 拖动时确保父控件不拦截
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = ev.x
                lastY = ev.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - lastX)
                val dy = Math.abs(ev.y - lastY)

                // 如果横向移动大于纵向移动，认为是横向滑动，请求父控件不拦截
                if (dx > dy && dx > slopPx) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (dy > dx && dy > slopPx) {
                    // 纵向滑动，允许父控件拦截
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                lastX = ev.x
                lastY = ev.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 当图表正在拖动时，拦截事件
        if (isDragging) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }
}
