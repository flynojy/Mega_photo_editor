package com.example.mega_photo.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 裁剪区域矩形 (屏幕坐标)
    private val cropRect = RectF()

    // 图片在屏幕上的实际显示区域 (边界)
    private val imageBounds = RectF()
    private var imageWidth = 0
    private var imageHeight = 0

    // 画笔定义
    private val shadowPaint = Paint().apply { color = Color.parseColor("#AA000000") }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val handlePaint = Paint().apply { color = Color.WHITE }

    private val handleRadius = 20f
    private val touchPadding = 60f
    private val minCropSize = 100f

    private var currentTouchMode = TOUCH_MODE_NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    companion object {
        private const val TOUCH_MODE_NONE = 0
        private const val TOUCH_MODE_TOP_LEFT = 1
        private const val TOUCH_MODE_TOP_RIGHT = 2
        private const val TOUCH_MODE_BOTTOM_LEFT = 3
        private const val TOUCH_MODE_BOTTOM_RIGHT = 4
        private const val TOUCH_MODE_DRAG_ALL = 5
    }

    private var isInitialized = false

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (width > 0 && height > 0) {
            if (imageWidth > 0 && imageHeight > 0) {
                calculateImageBounds()
            }
            if (!isInitialized) {
                resetCropRect() // 初始重置
                isInitialized = true
            }
        }
    }

    // [核心修复] 确保此方法存在
    fun setImageDimensions(w: Int, h: Int) {
        imageWidth = w
        imageHeight = h
        if (width > 0 && height > 0) {
            calculateImageBounds()
            resetCropRect()
        }
    }

    private fun calculateImageBounds() {
        if (imageWidth == 0 || imageHeight == 0 || width == 0 || height == 0) return

        val viewRatio = width.toFloat() / height
        val imgRatio = imageWidth.toFloat() / imageHeight

        var drawW = width.toFloat()
        var drawH = height.toFloat()

        if (imgRatio > viewRatio) {
            drawH = width / imgRatio
        } else {
            drawW = height * imgRatio
        }

        val left = (width - drawW) / 2
        val top = (height - drawH) / 2
        val right = left + drawW
        val bottom = top + drawH

        imageBounds.set(left, top, right, bottom)
    }

    fun resetCropRect() {
        if (imageBounds.isEmpty) {
            val marginW = width * 0.1f
            val marginH = height * 0.1f
            cropRect.set(marginW, marginH, width - marginW, height - marginH)
        } else {
            cropRect.set(imageBounds)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 遮罩
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, shadowPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), shadowPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, shadowPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, shadowPaint)
        // 边框
        canvas.drawRect(cropRect, borderPaint)
        // 手柄
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentTouchMode = getTouchMode(event.x, event.y)
                lastTouchX = event.x
                lastTouchY = event.y
                return currentTouchMode != TOUCH_MODE_NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (currentTouchMode != TOUCH_MODE_NONE) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    updateCropRect(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentTouchMode = TOUCH_MODE_NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getTouchMode(x: Float, y: Float): Int {
        if (isNearPoint(x, y, cropRect.left, cropRect.top)) return TOUCH_MODE_TOP_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.top)) return TOUCH_MODE_TOP_RIGHT
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom)) return TOUCH_MODE_BOTTOM_LEFT
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom)) return TOUCH_MODE_BOTTOM_RIGHT
        if (cropRect.contains(x, y)) return TOUCH_MODE_DRAG_ALL
        return TOUCH_MODE_NONE
    }

    private fun isNearPoint(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        return abs(x1 - x2) < touchPadding && abs(y1 - y2) < touchPadding
    }

    private fun updateCropRect(dx: Float, dy: Float) {
        var newLeft = cropRect.left
        var newTop = cropRect.top
        var newRight = cropRect.right
        var newBottom = cropRect.bottom

        val limitRect = if (!imageBounds.isEmpty) imageBounds else RectF(0f, 0f, width.toFloat(), height.toFloat())

        when (currentTouchMode) {
            TOUCH_MODE_TOP_LEFT -> { newLeft += dx; newTop += dy }
            TOUCH_MODE_TOP_RIGHT -> { newRight += dx; newTop += dy }
            TOUCH_MODE_BOTTOM_LEFT -> { newLeft += dx; newBottom += dy }
            TOUCH_MODE_BOTTOM_RIGHT -> { newRight += dx; newBottom += dy }
            TOUCH_MODE_DRAG_ALL -> {
                newLeft += dx; newTop += dy; newRight += dx; newBottom += dy
                val w = newRight - newLeft
                val h = newBottom - newTop
                if (newLeft < limitRect.left) { newLeft = limitRect.left; newRight = newLeft + w }
                if (newTop < limitRect.top) { newTop = limitRect.top; newBottom = newTop + h }
                if (newRight > limitRect.right) { newRight = limitRect.right; newLeft = newRight - w }
                if (newBottom > limitRect.bottom) { newBottom = limitRect.bottom; newTop = newBottom - h }
            }
        }

        if (currentTouchMode != TOUCH_MODE_DRAG_ALL) {
            if (newRight - newLeft < minCropSize) {
                if (currentTouchMode == TOUCH_MODE_TOP_LEFT || currentTouchMode == TOUCH_MODE_BOTTOM_LEFT) newLeft = newRight - minCropSize
                else newRight = newLeft + minCropSize
            }
            if (newBottom - newTop < minCropSize) {
                if (currentTouchMode == TOUCH_MODE_TOP_LEFT || currentTouchMode == TOUCH_MODE_TOP_RIGHT) newTop = newBottom - minCropSize
                else newBottom = newTop + minCropSize
            }
            newLeft = max(limitRect.left, min(newLeft, limitRect.right))
            newTop = max(limitRect.top, min(newTop, limitRect.bottom))
            newRight = max(limitRect.left, min(newRight, limitRect.right))
            newBottom = max(limitRect.top, min(newBottom, limitRect.bottom))
        }
        cropRect.set(newLeft, newTop, newRight, newBottom)
    }

    fun getNormalizedCropRect(): RectF {
        if (imageBounds.isEmpty) return RectF(0f, 0f, 1f, 1f)
        val l = (cropRect.left - imageBounds.left) / imageBounds.width()
        val t = (cropRect.top - imageBounds.top) / imageBounds.height()
        val r = (cropRect.right - imageBounds.left) / imageBounds.width()
        val b = (cropRect.bottom - imageBounds.top) / imageBounds.height()
        return RectF(max(0f, min(l, 1f)), max(0f, min(t, 1f)), max(0f, min(r, 1f)), max(0f, min(b, 1f)))
    }
}
