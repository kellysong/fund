package com.sjl.fund.util

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * 页面加载 loading 统一封装
 * 以覆盖层形式动态挂到 Activity 的内容根布局（android.R.id.content），
 * 各页面无需在自己的 XML 里放置 loading 控件，直接复用即可。
 * 采用 Material 细圆弧进度指示器，默认不带遮罩层（不拦截点击）。
 *
 * 用法：
 *   private val pageLoading by lazy { PageLoading(this) }
 *   加载开始 -> pageLoading.show()
 *   加载结束 -> pageLoading.hide()
 *
 * @param activity 宿主 Activity
 * @param tint     进度弧颜色，默认与项目主色一致
 * @param dim      是否加半透明遮罩并拦截点击，默认 false（无遮罩）
 */
class PageLoading(
    private val activity: Activity,
    private val tint: Int = android.graphics.Color.parseColor("#1677FF"),
    private val dim: Boolean = false
) {

    private var overlay: FrameLayout? = null

    /** 懒创建覆盖层，只挂一次，后续复用 */
    private fun ensureOverlay(): FrameLayout? {
        overlay?.let { return it }
        val root = activity.findViewById<View>(android.R.id.content) as? FrameLayout ?: return null
        val density = activity.resources.displayMetrics.density
        val progress = CircularProgressIndicator(activity).apply {
            isIndeterminate = true
            // 细圆弧：轨道(弧)粗细约 2dp，直径约 20dp
            setTrackThickness((2f * density).toInt())
            setIndicatorSize((20f * density).toInt())
            setIndicatorColor(tint)
        }
        val container = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            if (dim) {
                // 仅在开启遮罩时加半透明背景并拦截点击
                setBackgroundColor(android.graphics.Color.parseColor("#33000000"))
                isClickable = true
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addView(progress, lp)
            visibility = View.GONE
        }
        root.addView(container)
        overlay = container
        return container
    }

    /** 显示加载 */
    fun show() {
        ensureOverlay()?.visibility = View.VISIBLE
    }

    /** 隐藏加载 */
    fun hide() {
        overlay?.visibility = View.GONE
    }
}
