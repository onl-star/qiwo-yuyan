package com.yuyan.imemodule.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
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
        val caretX = totalPaddingLeft + layout.getPrimaryHorizontal(boundary)
        val top = extendedPaddingTop + layout.getLineTop(line).toFloat()
        val bottom = extendedPaddingTop + layout.getLineBottom(line).toFloat()
        caretPaint.color = currentTextColor
        canvas.drawLine(caretX, top, caretX, bottom, caretPaint)
    }
}
