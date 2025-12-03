package com.example.mega_photo.ui.custom

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat

/**
 * 自定义 View：流光文字效果
 * 使用 LinearGradient Shader + Matrix 平移实现
 */
class ShimmerTitleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var linearGradient: LinearGradient? = null
    private var gradientMatrix: Matrix? = null
    private var paint: Paint? = null
    private var viewWidth = 0
    private var animator: ValueAnimator? = null
    private var translate = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0) return
        viewWidth = w

        // 创建线性渐变：左边是原色，中间是高亮色(白色)，右边是原色
        // 这样高亮部分划过时就像一道光
        val colorPrimary = currentTextColor
        val colorHighlight = 0xFFFFFFFF.toInt() // 白色高亮

        linearGradient = LinearGradient(
            0f, 0f, viewWidth.toFloat(), 0f,
            intArrayOf(colorPrimary, colorHighlight, colorPrimary),
            floatArrayOf(0.3f, 0.5f, 0.7f), // 高亮在中间
            Shader.TileMode.CLAMP
        )

        paint = getPaint()
        paint?.shader = linearGradient
        gradientMatrix = Matrix()

        startShimmerAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gradientMatrix != null && linearGradient != null) {
            // 每次绘制时，改变 Matrix 的平移距离
            gradientMatrix?.setTranslate(translate, 0f)
            linearGradient?.setLocalMatrix(gradientMatrix)
        }
    }

    private fun startShimmerAnimation() {
        // 让光从左边(-width) 移动到 右边(+width)
        animator = ValueAnimator.ofFloat(-viewWidth.toFloat(), viewWidth.toFloat()).apply {
            duration = 2500 // 动画时长 2.5秒
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                translate = it.animatedValue as Float
                invalidate() // 触发重绘
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel() // 防止内存泄漏
    }
}