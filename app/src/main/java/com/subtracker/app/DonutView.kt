package com.subtracker.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

/** Simple donut chart: one coloured arc per category. */
class DonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.stroke)
    }
    private val rect = RectF()
    private var slices: List<Pair<Float, Int>> = emptyList()

    /** Each slice is (amount, colour). */
    fun setData(data: List<Pair<Float, Int>>) {
        slices = data.filter { it.first > 0f }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val stroke = size * 0.13f
        arcPaint.strokeWidth = stroke
        trackPaint.strokeWidth = stroke
        val pad = stroke / 2f
        rect.set(pad, pad, size - pad, size - pad)

        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        val total = slices.sumOf { it.first.toDouble() }.toFloat()
        if (total <= 0f) return
        var start = -90f
        for ((value, color) in slices) {
            val sweep = value / total * 360f
            arcPaint.color = color
            val gap = if (slices.size > 1) 2f else 0f
            canvas.drawArc(rect, start, max(sweep - gap, 0.5f), false, arcPaint)
            start += sweep
        }
    }
}
