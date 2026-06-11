package com.yuyan.imemodule.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView

class CompositionCaretTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextView(context, attrs, defStyleAttr) {

    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = context.resources.displayMetrics.density * 2f
    }
    private var compositionText: String = ""
    private var caretBoundary: Int? = null

    init {
        setHorizontallyScrolling(true)
        maxLines = 1
    }

    fun setComposition(text: String, caret: Int?) {
        compositionText = text
        caretBoundary = caret?.coerceIn(0, text.length)
        if (this.text.toString() != text) {
            this.text = text
        }
        invalidate()
    }

    fun resolveCaretBoundary(x: Float, y: Float): Int {
        if (compositionText.isEmpty()) return 0
        return getOffsetForPosition(x, y).coerceIn(0, compositionText.length)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val boundary = caretBoundary ?: return
        if (compositionText.isEmpty()) return
        val layout = layout ?: return
        val line = layout.getLineForOffset(boundary)
        val caretX = compoundPaddingLeft + layout.getPrimaryHorizontal(boundary)
        val textLayoutTop = compoundPaddingTop + verticalTextOffset(layout.height)
        val top = textLayoutTop + layout.getLineTop(line).toFloat()
        val bottom = textLayoutTop + layout.getLineBottom(line).toFloat()
        caretPaint.color = currentTextColor
        canvas.drawLine(caretX, top, caretX, bottom, caretPaint)
    }

    private fun verticalTextOffset(textHeight: Int): Float {
        val availableHeight = height - compoundPaddingTop - compoundPaddingBottom
        if (availableHeight <= textHeight) return 0f
        return when (gravity and Gravity.VERTICAL_GRAVITY_MASK) {
            Gravity.CENTER_VERTICAL -> (availableHeight - textHeight) / 2f
            Gravity.BOTTOM -> (availableHeight - textHeight).toFloat()
            else -> 0f
        }
    }
}
