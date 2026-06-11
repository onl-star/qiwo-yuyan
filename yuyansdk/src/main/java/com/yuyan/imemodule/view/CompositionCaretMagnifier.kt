package com.yuyan.imemodule.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import kotlin.math.max

class CompositionCaretMagnifier(private val context: Context) {

    private var onCaretChanged: ((Int) -> Int?)? = null

    private val preview = CompositionCaretTextView(context).apply {
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(16), 0)
        setBackgroundColor(Color.WHITE)
        setOnTouchListener { _, event ->
            handlePreviewTouch(event)
        }
    }
    private val popupWindow = PopupWindow(
        preview,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        isClippingEnabled = true
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = dp(6).toFloat()
        }
    }

    fun setOnCaretChanged(listener: ((Int) -> Int?)?) {
        onCaretChanged = listener
    }

    fun show(anchor: CompositionCaretTextView, text: String, caret: Int?, x: Float, y: Float) {
        if (text.isEmpty()) {
            dismiss()
            return
        }
        updatePreview(anchor, text, caret, x, y)
        popupWindow.width = max(anchor.width, dp(160))
        popupWindow.height = max(anchor.height * 2, dp(56))
        if (!popupWindow.isShowing) {
            popupWindow.showAsDropDown(anchor, 0, -anchor.height - popupWindow.height)
        } else {
            popupWindow.update(anchor, 0, -anchor.height - popupWindow.height, popupWindow.width, popupWindow.height)
        }
    }

    fun update(anchor: CompositionCaretTextView, text: String, caret: Int?, x: Float, y: Float) {
        if (!popupWindow.isShowing) {
            show(anchor, text, caret, x, y)
            return
        }
        updatePreview(anchor, text, caret, x, y)
        popupWindow.update(anchor, 0, -anchor.height - popupWindow.height, popupWindow.width, popupWindow.height)
    }

    fun finalizeCaret(anchor: CompositionCaretTextView, text: String, x: Float, y: Float): Int? {
        if (text.isEmpty()) {
            dismiss()
            return null
        }
        val boundary = anchor.resolveCaretBoundary(x, y)
        if (!popupWindow.isShowing) {
            show(anchor, text, boundary, x, y)
        } else {
            updatePreview(anchor, text, boundary, x, y)
            popupWindow.update(anchor, 0, -anchor.height - popupWindow.height, popupWindow.width, popupWindow.height)
        }
        return boundary
    }

    val isShowing: Boolean
        get() = popupWindow.isShowing

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun handlePreviewTouch(event: MotionEvent): Boolean {
        if (preview.text.isNullOrEmpty()) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val rawCaret = preview.resolveCaretBoundary(event.x, event.y)
                val canonicalCaret = onCaretChanged?.invoke(rawCaret) ?: rawCaret
                preview.setComposition(preview.text.toString(), canonicalCaret)
                true
            }
            MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }

    private fun updatePreview(anchor: CompositionCaretTextView, text: String, caret: Int?, x: Float, y: Float) {
        preview.setTextColor(anchor.currentTextColor)
        preview.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchor.textSize * 1.6f)
        preview.setComposition(text, caret ?: anchor.resolveCaretBoundary(x, y))
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
