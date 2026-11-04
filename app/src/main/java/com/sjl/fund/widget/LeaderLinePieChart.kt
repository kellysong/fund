package com.sjl.fund.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.Utils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 带引导线标签的饼图（类似 iOS Charts 的 x-entry 效果）
 * 每个扇区从弧线中点向外引一条折线（斜线段 + 水平短线），在折线末端标注“名称 百分比”
 * 小占比扇区标签也能完整显示，替代被裁剪的底部图例；建议配合
 * setDrawEntryLabels(false)、legend.isEnabled = false 使用
 */
class LeaderLinePieChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : PieChart(context, attrs, defStyle) {

    /** 斜引导线长度（px） */
    private val leaderLen = Utils.convertDpToPixel(14f)

    /** 水平短线长度（px） */
    private val horizLen = Utils.convertDpToPixel(10f)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = Utils.convertDpToPixel(11f)
        color = Color.parseColor("#333333")
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = Utils.convertDpToPixel(1f)
        color = Color.parseColor("#BFBFBF")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawLeaderLabels(canvas)
    }

    /**
     * 在扇区外侧绘制折线引导 + 文本标签
     * 依赖父类绘制时缓存的 drawAngles/absoluteAngles 与当前旋转角（含动画中间状态）
     */
    private fun drawLeaderLabels(canvas: Canvas) {
        val data = this.data ?: return
        val dataSet = data.getDataSetByIndex(0) ?: return
        if (dataSet.entryCount == 0) return

        val center = centerCircleBox
        val radius = this.radius
        val sliceSpace = dataSet.sliceSpace
        val rotationAngle = this.rotationAngle
        // 各扇区颜色（与 updateAssetAllocation 中的顺序一致）
        val colors = dataSet.colors

        // 记录左右两侧上一标签的 y，避免相邻小扇区标签重叠
        var lastLeftY = Float.MIN_VALUE
        var lastRightY = Float.MIN_VALUE

        for (i in 0 until dataSet.entryCount) {
            val sliceAngle = abs(drawAngles[i])
            if (sliceAngle < 0.001f) continue
            val entry = dataSet.getEntryForIndex(i)
            val value = entry.y

            // 扇区起始角（不含旋转）= 前一个扇区的累计角 absoluteAngles[i-1]；absoluteAngles 为含自身的累计值
            val startAngle = if (i == 0) 0f else absoluteAngles[i - 1]
            // 与 PieChartRenderer 一致：屏幕角度 = rotationAngle + 起始角 + 本扇区扫过角/2，使用标准 cos/sin（y 轴向下不取负）
            val midDeg = rotationAngle + startAngle + drawAngles[i] / 2f
            val rad = Math.toRadians(midDeg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            // 起点：扇区弧外沿
            val startX = center.x + cosA * (radius + sliceSpace / 2f)
            val startY = center.y + sinA * (radius + sliceSpace / 2f)
            // 拐点：沿径向向外引出
            val bendX = startX + cosA * leaderLen
            val bendY = startY + sinA * leaderLen
            // 右侧还是左侧标签（同时受控件左右边界约束）
            val onRight = bendX >= center.x && bendX + horizLen + textPaint.measureText(buildLabel(entry, value)) / 2f <= width - 4f
            val endX = bendX + if (onRight) horizLen else -horizLen
            var labelY = bendY

            // 与同侧上一个标签保持最小间距
            val minGap = textPaint.textSize + Utils.convertDpToPixel(2f)
            if (onRight) {
                if (labelY - lastRightY < minGap && lastRightY != Float.MIN_VALUE) labelY = lastRightY + minGap
                lastRightY = labelY
            } else {
                if (labelY - lastLeftY < minGap && lastLeftY != Float.MIN_VALUE) labelY = lastLeftY + minGap
                lastLeftY = labelY
            }

            // 引导折线（颜色取扇区色，与 iOS Charts pointer 类似）
            val sliceColor = colors[i % colors.size]
            linePaint.color = blendGray(sliceColor)
            canvas.drawLine(startX, startY, bendX, bendY, linePaint)
            canvas.drawLine(bendX, bendY, endX, labelY, linePaint)

            // 文本标签：右侧左对齐、左侧右对齐，保证文本从水平短线末端向外延伸
            val label = buildLabel(entry, value)
            textPaint.textAlign = if (onRight) Paint.Align.LEFT else Paint.Align.RIGHT
            canvas.drawText(label, endX, labelY + textPaint.textSize / 3f, textPaint)
        }
    }

    private fun buildLabel(entry: PieEntry, value: Float): String {
        val name = entry.label ?: ""
        return if (name.isEmpty()) String.format("%.1f%%", value)
        else "$name ${String.format("%.1f%%", value)}"
    }

    /** 引导线颜色：扇区色淡化，避免过艳 */
    private fun blendGray(color: Int): Int {
        val r = (Color.red(color) * 0.5f + 191 * 0.5f).toInt()
        val g = (Color.green(color) * 0.5f + 191 * 0.5f).toInt()
        val b = (Color.blue(color) * 0.5f + 191 * 0.5f).toInt()
        return Color.rgb(r, g, b)
    }
}
