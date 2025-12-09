package com.example.mega_photo.ui.editor

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mega_photo.data.FilterItem
import com.example.mega_photo.databinding.ActivityEditorBinding
import com.example.mega_photo.ui.adapter.FilterAdapter
import com.example.mega_photo.utils.BitmapUtils
import com.example.mega_photo.utils.CubeLutData
import com.example.mega_photo.utils.CubeLutParser
import com.example.mega_photo.utils.FileSaver
import com.example.mega_photo.utils.LutUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class EditorActivity : AppCompatActivity() {

    private val TAG = "EditorActivity"
    private lateinit var binding: ActivityEditorBinding
    private lateinit var renderer: PhotoRenderer
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val stateManager = StateManager(maxHistory = 10)
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScaling = false
    private var isCropMode = false
    private var resetTouchAnchor = false

    private val lutCache = ConcurrentHashMap<String, CubeLutData>()

    private val allFilters = mutableListOf<FilterItem>()
    private lateinit var filterAdapter: FilterAdapter

    private val pickLutLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            showLutNameDialog(uri)
        }
    }

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

    private fun showLutNameDialog(uri: Uri) {
        val input = EditText(this)
        input.hint = "请输入滤镜名称 (例如: My Style)"
        AlertDialog.Builder(this)
            .setTitle("导入 LUT")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    importLut(uri, name)
                } else {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupFilters() {
        allFilters.clear()
        allFilters.add(FilterItem("Original", null, "lut_example/original.jpg"))
        allFilters.add(FilterItem("Koto", "luts/KOTO.cube", "lut_example/koto.jpg"))
        allFilters.add(FilterItem("Taipei", "luts/TAIPEI.cube", "lut_example/taipai.jpg"))
        allFilters.add(FilterItem("Greenland", "luts/GREENLAND.cube", "lut_example/greenland.jpg"))
        allFilters.add(FilterItem("Nightscape", "luts/NIGHTSCAPE.cube", "lut_example/nightscape.jpg"))
        allFilters.add(FilterItem("Holiday", "luts/HOLIDAY.cube", "lut_example/holiday.jpg"))
        allFilters.add(FilterItem("Tokyo", "luts/TOKYO METRO.cube", "lut_example/tokyo.jpg"))
        allFilters.add(FilterItem("Gaomei", "luts/GAOMEI.cube", "lut_example/gaomei.jpg"))
        allFilters.add(FilterItem("Blaze", "luts/BLAZE LT.cube", "lut_example/blaze.jpg"))

        val imported = LutUtils.getImportedLuts(this)
        imported.forEach { (lutPath, previewPath) ->
            val fileName = lutPath.substringAfterLast("/")
            val name = fileName.replace(".cube", "")
            allFilters.add(FilterItem(name, lutPath, previewPath))
        }

        binding.rvFilters.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        filterAdapter = FilterAdapter(
            filters = allFilters,
            onFilterClick = { filter ->
                if (filter == null) {
                    pickLutLauncher.launch(arrayOf("*/*"))
                } else {
                    applyFilter(filter)
                }
            },
            onFilterLongClick = { filter ->
                handleLongClick(filter)
            }
        )
        binding.rvFilters.adapter = filterAdapter

        preloadAllFilters(allFilters)
    }

    private fun handleLongClick(filter: FilterItem) {
        val path = filter.lutFileName ?: return
        val isImported = path.startsWith("/")

        if (!isImported) {
            Toast.makeText(this, "内置滤镜不可删除", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("删除滤镜")
            .setMessage("确定要删除 \"${filter.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteLut(filter)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // [修改] 删除逻辑 (带动画)
    private fun deleteLut(filter: FilterItem) {
        val index = allFilters.indexOf(filter)
        if (index == -1) return

        // index + 1 因为 adapter 第0位是 add 按钮
        val adapterPosition = index + 1
        val view = binding.rvFilters.layoutManager?.findViewByPosition(adapterPosition)

        if (view != null) {
            view.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .setDuration(300)
                .withEndAction {
                    performDelete(filter, index)
                    // 复原 View 状态供复用
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
                .start()
        } else {
            performDelete(filter, index)
        }
    }

    private fun performDelete(filter: FilterItem, index: Int) {
        val lutPath = filter.lutFileName ?: return
        val previewPath = filter.previewFileName ?: return

        if (LutUtils.deleteImportedLut(this, lutPath, previewPath)) {
            lutCache.remove(lutPath)
            allFilters.removeAt(index)
            // 刷新列表
            filterAdapter.notifyItemRemoved(index + 1) // +1 header
            filterAdapter.notifyItemRangeChanged(index + 1, allFilters.size - index)

            val currentState = renderer.getCurrentState()
            if (currentState.filterItem.lutFileName == lutPath) {
                applyFilter(allFilters[0])
            }

            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importLut(uri: Uri, name: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val safeName = name.replace("[^a-zA-Z0-9_\\u4e00-\\u9fa5]".toRegex(), "_")
                val fileName = "$safeName.cube"
                val lutFile = LutUtils.copyUriToInternalStorage(this@EditorActivity, uri, fileName)

                if (lutFile != null) {
                    val lutData = CubeLutParser.load(this@EditorActivity, lutFile.absolutePath)

                    if (lutData != null) {
                        var baseBitmap: Bitmap? = null
                        try {
                            val basePreviewStream = assets.open("lut_example/original.jpg")
                            baseBitmap = BitmapFactory.decodeStream(basePreviewStream)
                        } catch (e: Exception) {
                            baseBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
                            baseBitmap.eraseColor(Color.LTGRAY)
                        }

                        val previewBitmap = LutUtils.applyLutToBitmapCpu(lutData, baseBitmap!!)
                        val previewName = fileName.replace(".cube", ".jpg")
                        val previewPath = LutUtils.saveBitmapToInternalStorage(this@EditorActivity, previewBitmap, previewName)

                        if (previewPath != null) {
                            val newItem = FilterItem(name, lutFile.absolutePath, previewPath)
                            lutCache[lutFile.absolutePath] = lutData

                            withContext(Dispatchers.Main) {
                                allFilters.add(newItem)
                                // [修改] 使用 notifyItemInserted 替代全量刷新，增加动画
                                filterAdapter.notifyItemInserted(allFilters.size) // +1(header) -1(index) +1(new) = size
                                binding.rvFilters.smoothScrollToPosition(allFilters.size)
                                Toast.makeText(this@EditorActivity, "导入成功: $name", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditorActivity, "导入出错", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun preloadAllFilters(filters: List<FilterItem>) {
        lifecycleScope.launch(Dispatchers.IO) {
            filters.forEach { item ->
                val path = item.lutFileName
                if (path != null && !lutCache.containsKey(path)) {
                    val data = CubeLutParser.load(this@EditorActivity, path)
                    if (data != null) {
                        lutCache[path] = data
                    }
                }
            }
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

        binding.btnCropCancel.setOnClickListener {
            renderer.resetView()
            renderer.setTempScale(1.0f)
            binding.glSurfaceView.requestRender()
            exitCropMode()
        }

        binding.btnCropConfirm.setOnClickListener {
            val normalizedRect = binding.cropOverlayView.getNormalizedCropRect()
            renderer.applyCrop(normalizedRect) { newW, newH ->
                runOnUiThread {
                    binding.cropOverlayView.setImageDimensions(newW, newH)
                    saveCurrentState()
                }
            }
            renderer.setTempScale(1.0f)
            exitCropMode()
        }
    }

    private fun enterCropMode() {
        renderer.resetView()
        renderer.setTempScale(0.8f)
        binding.glSurfaceView.requestRender()
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

        binding.btnResetAdjust.setOnClickListener {
            binding.seekBrightness.progress = 50
            binding.seekContrast.progress = 50
            binding.seekSaturation.progress = 50
            updateRendererAdjustments()
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
                resetTouchAnchor = true
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
                resetTouchAnchor = false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                resetTouchAnchor = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && !scaleGestureDetector.isInProgress && event.pointerCount == 1) {
                    if (resetTouchAnchor) {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        resetTouchAnchor = false
                        return
                    }
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
                resetTouchAnchor = false
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
            val fileName = filter.lutFileName
            if (lutCache.containsKey(fileName)) {
                renderer.setCubeLut(lutCache[fileName])
                binding.glSurfaceView.requestRender()
                if (saveState) saveCurrentState()
            } else {
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.IO) {
                    val lutData = CubeLutParser.load(this@EditorActivity, fileName)
                    if (lutData != null) {
                        lutCache[fileName] = lutData
                    }
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
