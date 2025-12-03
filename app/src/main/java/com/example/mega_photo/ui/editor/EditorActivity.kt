package com.example.mega_photo.ui.editor

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mega_photo.data.FilterItem
import com.example.mega_photo.databinding.ActivityEditorBinding
import com.example.mega_photo.ui.adapter.FilterAdapter
import com.example.mega_photo.utils.BitmapUtils
import com.example.mega_photo.utils.CubeLutParser
import com.example.mega_photo.utils.FileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var renderer: PhotoRenderer
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    // 状态管理器
    private val stateManager = StateManager(maxHistory = 10)

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false
    private var isCropMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriStr = intent.getStringExtra("KEY_IMAGE_URI")
        if (uriStr == null) {
            finish(); return
        }

        binding.glSurfaceView.setEGLContextClientVersion(3)
        renderer = PhotoRenderer(this)

        val bitmap = BitmapUtils.loadBitmapFromUri(this, Uri.parse(uriStr), 2048, 2048)
        if (bitmap != null) {
            renderer.setImage(bitmap)
            binding.glSurfaceView.setRenderer(renderer)
            binding.glSurfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
            binding.cropOverlayView.setImageDimensions(bitmap.width, bitmap.height)

            // 初始化初始状态
            binding.glSurfaceView.post {
                val initialState = renderer.getCurrentState()
                stateManager.initialize(initialState)
                updateUndoRedoButtons()
            }
        }

        setupFilters()
        setupGestures()
        setupBottomBar()
        setupAdjustments()
        setupCropUI()
        setupUndoRedo()

        binding.btnSave.setOnClickListener { showSaveDialog() }
    }

    // [核心修复] 补充预览图参数
    private fun setupFilters() {
        val filters = listOf(
            FilterItem("Original", null, "lut_example/original.jpg"),
            FilterItem("Koto", "luts/KOTO.cube", "lut_example/koto.jpg"),
            FilterItem("Taipei", "luts/TAIPEI.cube", "lut_example/taipai.jpg"),
            FilterItem("Greenland", "luts/GREENLAND.cube", "lut_example/greenland.jpg"),
            FilterItem("Nightscape", "luts/NIGHTSCAPE.cube", "lut_example/nightscape.jpg"),
            FilterItem("Holiday", "luts/HOLIDAY.cube", "lut_example/holiday.jpg"),
            FilterItem("Tokyo", "luts/TOKYO METRO.cube", "lut_example/tokyo.jpg"),
            FilterItem("Gaomei", "luts/GAOMEI.cube", "lut_example/gaomei.jpg"),
            FilterItem("Blaze", "luts/BLAZE LT.cube", "lut_example/blaze.jpg")
        )

        binding.rvFilters.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilters.adapter = FilterAdapter(filters) { filter ->
            applyFilter(filter)
        }
    }

    private fun setupUndoRedo() {
        binding.btnUndo.setOnClickListener {
            val state = stateManager.undo()
            if (state != null) applyState(state)
            updateUndoRedoButtons()
        }
        binding.btnRedo.setOnClickListener {
            val state = stateManager.redo()
            if (state != null) applyState(state)
            updateUndoRedoButtons()
        }
    }

    private fun updateUndoRedoButtons() {
        binding.btnUndo.isEnabled = stateManager.canUndo()
        binding.btnUndo.alpha = if (stateManager.canUndo()) 1.0f else 0.5f

        binding.btnRedo.isEnabled = stateManager.canRedo()
        binding.btnRedo.alpha = if (stateManager.canRedo()) 1.0f else 0.5f
    }

    private fun saveCurrentState() {
        val state = renderer.getCurrentState()
        stateManager.commit(state)
        updateUndoRedoButtons()
    }

    private fun applyState(state: EditorState) {
        renderer.restoreState(state)

        binding.seekBrightness.progress = ((state.brightness * 100) + 50).toInt()
        binding.seekContrast.progress = (state.contrast * 50).toInt()
        binding.seekSaturation.progress = (state.saturation * 50).toInt()

        applyFilter(state.filterItem, saveState = false)
        binding.glSurfaceView.requestRender()
    }

    private fun setupCropUI() {
        binding.btnCrop.setOnClickListener { enterCropMode() }
        binding.btnCropCancel.setOnClickListener { exitCropMode() }
        binding.btnCropConfirm.setOnClickListener {
            val normalizedRect = binding.cropOverlayView.getNormalizedCropRect()
            renderer.applyCrop(normalizedRect) { newW, newH ->
                runOnUiThread {
                    binding.cropOverlayView.setImageDimensions(newW, newH)
                    saveCurrentState()
                }
            }
            exitCropMode()
        }
    }

    private fun enterCropMode() {
        isCropMode = true
        binding.cropOverlayView.visibility = View.VISIBLE
        binding.cropConfirmBar.visibility = View.VISIBLE
        binding.cropOverlayView.resetCropRect()
        binding.toolsContainer.visibility = View.GONE
        binding.bottomNavBar.visibility = View.GONE
        binding.btnSave.visibility = View.GONE
        binding.btnUndo.visibility = View.GONE
        binding.btnRedo.visibility = View.GONE
    }

    private fun exitCropMode() {
        isCropMode = false
        binding.cropOverlayView.visibility = View.GONE
        binding.cropConfirmBar.visibility = View.GONE
        binding.toolsContainer.visibility = View.VISIBLE
        binding.bottomNavBar.visibility = View.VISIBLE
        binding.btnSave.visibility = View.VISIBLE
        binding.btnUndo.visibility = View.VISIBLE
        binding.btnRedo.visibility = View.VISIBLE
    }

    private fun setupAdjustments() {
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                updateRendererAdjustments()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                saveCurrentState()
            }
        }
        binding.seekBrightness.setOnSeekBarChangeListener(listener)
        binding.seekContrast.setOnSeekBarChangeListener(listener)
        binding.seekSaturation.setOnSeekBarChangeListener(listener)

        // [新增] 重置调色
        binding.btnResetAdjust.setOnClickListener {
            // 1. 重置 UI (fromUser = false，不会触发 listener 中的 update)
            binding.seekBrightness.progress = 50
            binding.seekContrast.progress = 50
            binding.seekSaturation.progress = 50

            // 2. 手动应用重置后的参数
            updateRendererAdjustments()

            // 3. 保存状态
            saveCurrentState()
        }
    }

    private fun updateRendererAdjustments() {
        val br = (binding.seekBrightness.progress - 50) / 100.0f
        val ct = binding.seekContrast.progress / 50.0f
        val sa = binding.seekSaturation.progress / 50.0f
        renderer.setAdjustments(br, ct, sa)
        binding.glSurfaceView.requestRender()
    }

    private fun setupBottomBar() {
        binding.tabEdit.setOnClickListener { switchTab(binding.tabEdit); binding.panelEdit.visibility = View.VISIBLE; binding.rvFilters.visibility = View.GONE; binding.panelAdjust.visibility = View.GONE }
        binding.tabFilters.setOnClickListener { switchTab(binding.tabFilters); binding.panelEdit.visibility = View.GONE; binding.rvFilters.visibility = View.VISIBLE; binding.panelAdjust.visibility = View.GONE }
        binding.tabAdjust.setOnClickListener { switchTab(binding.tabAdjust); binding.panelEdit.visibility = View.GONE; binding.rvFilters.visibility = View.GONE; binding.panelAdjust.visibility = View.VISIBLE }

        binding.btnRotateLeft.setOnClickListener {
            renderer.rotateLeft()
            binding.glSurfaceView.requestRender()
            saveCurrentState()
        }
        binding.btnRotateRight.setOnClickListener {
            renderer.rotateRight()
            binding.glSurfaceView.requestRender()
            saveCurrentState()
        }
        binding.btnFlip.setOnClickListener {
            renderer.flipHorizontal()
            binding.glSurfaceView.requestRender()
            saveCurrentState()
        }
        switchTab(binding.tabEdit)
    }

    private fun switchTab(selectedTab: TextView) {
        val tabs = listOf(binding.tabEdit, binding.tabFilters, binding.tabAdjust)
        for (tab in tabs) {
            tab.setTextColor(Color.parseColor("#888888"))
            tab.typeface = android.graphics.Typeface.DEFAULT
        }
        selectedTab.setTextColor(Color.WHITE)
        selectedTab.typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun setupGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isCropMode) return false
                val scaleFactor = detector.scaleFactor
                renderer.updateTransform(0f, 0f, scaleFactor)
                binding.glSurfaceView.requestRender()
                return true
            }
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (isCropMode) return false
                isScaling = true
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                saveCurrentState()
            }
        })

        binding.glSurfaceView.setOnTouchListener { v, event ->
            handleTouch(event)
            !isCropMode
        }
    }

    private fun handleTouch(event: MotionEvent) {
        if (isCropMode) return
        scaleGestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val glDx = (dx / binding.glSurfaceView.width) * 2.0f
                    val glDy = -(dy / binding.glSurfaceView.height) * 2.0f
                    renderer.updateTransform(glDx, glDy, 1.0f)
                    binding.glSurfaceView.requestRender()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!isScaling) saveCurrentState()
            }
        }
    }

    private fun applyFilter(filter: FilterItem, saveState: Boolean = true) {
        renderer.updateFilterRecord(filter)

        if (filter.lutFileName == null) {
            renderer.setCubeLut(null)
            binding.glSurfaceView.requestRender()
            if (saveState) saveCurrentState()
        } else {
            binding.progressBar.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                val lutData = CubeLutParser.parse(this@EditorActivity, filter.lutFileName)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (lutData != null) {
                        renderer.setCubeLut(lutData)
                        binding.glSurfaceView.requestRender()
                        if (saveState) saveCurrentState()
                    } else {
                        Toast.makeText(this@EditorActivity, "LUT 解析失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showSaveDialog() {
        val options = arrayOf("JPG (照片)", "PNG (无损)")
        AlertDialog.Builder(this)
            .setTitle("选择导出格式")
            .setItems(options) { _, which ->
                val format = if (which == 0) FileSaver.Format.JPG else FileSaver.Format.PNG
                performSave(format)
            }
            .show()
    }

    private fun performSave(format: FileSaver.Format) {
        binding.progressBar.visibility = View.VISIBLE
        renderer.saveImage { bitmap ->
            lifecycleScope.launch(Dispatchers.IO) {
                val uri = FileSaver.saveBitmapToGallery(this@EditorActivity, bitmap, format)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (uri != null) {
                        Toast.makeText(this@EditorActivity, "保存成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@EditorActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.glSurfaceView.requestRender()
    }
}
