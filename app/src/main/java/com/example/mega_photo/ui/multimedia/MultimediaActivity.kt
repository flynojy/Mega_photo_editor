package com.example.mega_photo.ui.multimedia

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.mega_photo.databinding.ActivityMultimediaBinding
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MultimediaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultimediaBinding
    private var isVideo = false
    private val handler = Handler(Looper.getMainLooper())
    private var isDraggingSeekBar = false

    // 记录视频原始尺寸
    private var videoWidth = 0
    private var videoHeight = 0

    private lateinit var gestureDetector: GestureDetector
    private var isUiVisible = true

    // 定时更新进度条的任务
    private val updateProgressAction = object : Runnable {
        override fun run() {
            if (binding.videoView.isPlaying && !isDraggingSeekBar) {
                val current = binding.videoView.currentPosition
                binding.seekBar.progress = current
                binding.tvCurrentTime.text = stringForTime(current)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMultimediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.data
        if (uri == null) {
            Toast.makeText(this, "文件加载失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initGestures()

        binding.btnBack.setOnClickListener { finish() }

        val mimeType = contentResolver.getType(uri) ?: ""
        binding.tvFileName.text = "预览"

        if (mimeType.contains("video")) {
            setupVideoPlayer(uri)
        } else if (mimeType.contains("gif") || mimeType.contains("image")) {
            setupGifPlayer(uri)
        } else {
            Toast.makeText(this, "不支持的文件格式: $mimeType", Toast.LENGTH_LONG).show()
        }

        // [核心修复] 监听布局变化 (自动处理旋转)
        // 当屏幕旋转后，View 尺寸改变，这里会触发，从而重新计算视频大小
        binding.root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                adjustVideoSize()
            }
        }
    }

    private fun initGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleUiVisibility()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isVideo) {
                    toggleVideoPlayback()
                }
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        val touchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
        binding.root.setOnTouchListener(touchListener)
        binding.videoView.setOnTouchListener(touchListener)
        binding.gifImageView.setOnTouchListener(touchListener)
    }

    private fun toggleUiVisibility() {
        isUiVisible = !isUiVisible
        val visibility = if (isUiVisible) View.VISIBLE else View.GONE

        binding.topBar.visibility = visibility
        if (isVideo) {
            binding.videoControls.visibility = visibility
        }
    }

    private fun toggleVideoPlayback() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.btnPlayPause.text = "播放"
        } else {
            binding.videoView.start()
            binding.btnPlayPause.text = "暂停"
        }
    }

    private fun setupGifPlayer(uri: Uri) {
        isVideo = false
        binding.gifImageView.visibility = View.VISIBLE
        binding.videoView.visibility = View.GONE
        binding.videoControls.visibility = View.GONE

        Glide.with(this)
            .asGif()
            .load(uri)
            .into(binding.gifImageView)
    }

    private fun setupVideoPlayer(uri: Uri) {
        isVideo = true
        binding.videoView.visibility = View.VISIBLE
        binding.gifImageView.visibility = View.GONE
        binding.videoControls.visibility = View.VISIBLE

        binding.videoView.setVideoURI(uri)

        binding.videoView.setOnPreparedListener { mp ->
            val duration = mp.duration
            binding.seekBar.max = duration
            binding.tvTotalTime.text = stringForTime(duration)

            // 记录并调整视频尺寸
            videoWidth = mp.videoWidth
            videoHeight = mp.videoHeight
            adjustVideoSize()

            mp.start()
            binding.btnPlayPause.text = "暂停"
            handler.post(updateProgressAction)
        }

        binding.videoView.setOnCompletionListener {
            binding.btnPlayPause.text = "播放"
            binding.videoView.seekTo(0)
            if (!isUiVisible) toggleUiVisibility()
        }

        binding.btnPlayPause.setOnClickListener {
            toggleVideoPlayback()
        }

        binding.btnRewind.setOnClickListener {
            val current = binding.videoView.currentPosition
            val target = max(0, current - 5000)
            binding.videoView.seekTo(target)
            binding.seekBar.progress = target
            binding.tvCurrentTime.text = stringForTime(target)
        }

        binding.btnForward.setOnClickListener {
            val current = binding.videoView.currentPosition
            val duration = binding.videoView.duration
            val target = min(duration, current + 5000)
            binding.videoView.seekTo(target)
            binding.seekBar.progress = target
            binding.tvCurrentTime.text = stringForTime(target)
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = stringForTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDraggingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingSeekBar = false
                binding.videoView.seekTo(seekBar?.progress ?: 0)
            }
        })
    }

    // [核心修复] 动态调整 VideoView 大小以保持比例 (Fit Center)
    private fun adjustVideoSize() {
        if (videoWidth == 0 || videoHeight == 0) return

        binding.videoView.post {
            val viewWidth = binding.root.width
            val viewHeight = binding.root.height
            if (viewWidth == 0 || viewHeight == 0) return@post

            val videoRatio = videoWidth.toFloat() / videoHeight
            val screenRatio = viewWidth.toFloat() / viewHeight

            val lp = binding.videoView.layoutParams

            var newW = viewWidth
            var newH = viewHeight

            if (videoRatio > screenRatio) {
                // 视频比屏幕宽：宽度撑满，高度自适应
                newH = (viewWidth / videoRatio).toInt()
            } else {
                // 视频比屏幕高：高度撑满，宽度自适应
                newW = (viewHeight * videoRatio).toInt()
            }

            // 只有尺寸真正改变时才更新，防止死循环
            if (lp.width != newW || lp.height != newH) {
                lp.width = newW
                lp.height = newH
                binding.videoView.layoutParams = lp
            }
        }
    }

    private fun stringForTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)
    }
}