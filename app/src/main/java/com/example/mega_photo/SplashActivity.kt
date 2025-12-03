package com.example.mega_photo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.mega_photo.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 减少启动延迟，让用户感觉更灵敏 (300 -> 100)
        binding.root.postDelayed({
            startMegaAnimation()
        }, 100)
    }

    private fun startMegaAnimation() {
        // 1. 创建文字跳跃动画
        val jumpHeight = -80f // 稍微减小跳跃高度，配合快节奏
        val duration = 300L   // [修改] 加快速度: 600ms -> 300ms

        val animM = createJumpAnimator(binding.tvM, jumpHeight, duration)
        val animE = createJumpAnimator(binding.tvE, jumpHeight, duration)
        val animG = createJumpAnimator(binding.tvG, jumpHeight, duration)
        val animA = createJumpAnimator(binding.tvA, jumpHeight, duration)

        // 2. 编排顺序：更加紧凑的依次跳跃
        val animatorSet = AnimatorSet()
        // 可以让它们稍微重叠一点播放，或者紧接着播放
        animatorSet.playSequentially(animM, animE, animG, animA)

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 文字跳完后，立即开始白色过渡
                startWhiteTransition()
            }
        })

        animatorSet.start()
    }

    private fun createJumpAnimator(view: View, height: Float, dur: Long): ObjectAnimator {
        // 依然是 上 -> 下 的完整动作
        val animator = ObjectAnimator.ofFloat(view, "translationY", 0f, height, 0f)
        animator.duration = dur
        animator.interpolator = AccelerateDecelerateInterpolator()
        return animator
    }

    private fun startWhiteTransition() {
        binding.whiteOverlay.visibility = View.VISIBLE

        // 3. 执行白色淡入动画
        binding.whiteOverlay.animate()
            .alpha(1f)
            .setDuration(400) // [修改] 加快过渡: 600ms -> 400ms
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    navigateToMain()
                }
            })
            .start()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        // 4. 保持淡出转场
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        finish()
    }
}