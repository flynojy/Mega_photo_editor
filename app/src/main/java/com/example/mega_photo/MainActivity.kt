package com.example.mega_photo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mega_photo.databinding.ActivityMainBinding
import com.example.mega_photo.ui.adapter.HomeMenuAdapter
import com.example.mega_photo.ui.adapter.MenuItem
import com.example.mega_photo.ui.gallery.GalleryActivity
import com.example.mega_photo.ui.multimedia.MultimediaActivity
import kotlin.math.hypot
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val ACTION_GALLERY = 1
        const val ACTION_MULTIMEDIA = 2
    }

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val intent = Intent(this, MultimediaActivity::class.java)
            intent.data = uri
            startActivity(intent)
        } else {
            Toast.makeText(this, "未选择文件", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMenu()

        // [新增] 绑定开发者信息按钮
        binding.btnDeveloperInfo.setOnClickListener {
            showDeveloperInfo()
        }
    }

    // [新增] 显示开发者信息弹窗
    private fun showDeveloperInfo() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_developer_info, null)
        val ivAvatar = dialogView.findViewById<ImageView>(R.id.ivDeveloperAvatar)

        // 加载 Assets 中的头像
        // Glide 支持 file:///android_asset/ 路径
        Glide.with(this)
            .load("file:///android_asset/my_profile/tx.jpg")
            .placeholder(android.R.drawable.ic_menu_gallery) // 占位图
            .error(android.R.drawable.ic_delete) // 错误图
            .into(ivAvatar)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // 设置背景透明，以便显示 CardView 的圆角
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun setupMenu() {
        val menuItems = listOf(
            MenuItem("我的相册", ACTION_GALLERY),
            MenuItem("多媒体播放", ACTION_MULTIMEDIA)
        )

        binding.rvMenu.layoutManager = LinearLayoutManager(this)
        binding.rvMenu.adapter = HomeMenuAdapter(menuItems) { item, view ->
            performRevealAnimation(view) {
                when (item.actionId) {
                    ACTION_GALLERY -> {
                        startActivity(Intent(this, GalleryActivity::class.java))
                        overridePendingTransition(0, 0)
                    }
                    ACTION_MULTIMEDIA -> {
                        pickMediaLauncher.launch(arrayOf("video/mp4", "image/gif"))
                        overridePendingTransition(0, 0)
                    }
                }

                view.postDelayed({
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.elevation = 8f
                    view.findViewById<View>(R.id.tvTitle)?.apply {
                        scaleX = 1f
                        scaleY = 1f
                    }
                }, 1000)
            }
        }
    }

    private fun performRevealAnimation(view: View, onAnimationEnd: () -> Unit) {
        view.elevation = 100f
        val textView = view.findViewById<View>(R.id.tvTitle)

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val cx = location[0] + view.width / 2
        val cy = location[1] + view.height / 2

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val d1 = hypot(cx.toDouble(), cy.toDouble())
        val d2 = hypot((screenW - cx).toDouble(), cy.toDouble())
        val d3 = hypot(cx.toDouble(), (screenH - cy).toDouble())
        val d4 = hypot((screenW - cx).toDouble(), (screenH - cy).toDouble())

        val maxDist = max(max(d1, d2), max(d3, d4)).toFloat()
        val finalScale = (maxDist * 2.5f) / view.width

        val animator = ValueAnimator.ofFloat(1f, finalScale)
        animator.duration = 400
        animator.interpolator = AccelerateInterpolator()

        animator.addUpdateListener { animation ->
            val scale = animation.animatedValue as Float
            view.scaleX = scale
            view.scaleY = scale

            if (textView != null) {
                val inverseScale = 1f / scale
                textView.scaleX = inverseScale
                textView.scaleY = inverseScale
            }
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onAnimationEnd()
            }
        })

        animator.start()
    }
}
