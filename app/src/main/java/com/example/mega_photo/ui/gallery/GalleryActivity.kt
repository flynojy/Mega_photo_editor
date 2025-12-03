package com.example.mega_photo.ui.gallery


import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.mega_photo.data.MediaItem
import com.example.mega_photo.databinding.ActivityGalleryBinding
import com.example.mega_photo.ui.adapter.GalleryAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import com.example.mega_photo.ui.editor.EditorActivity

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val galleryAdapter = GalleryAdapter { item ->
        // 跳转到编辑器，传递图片 URI
        val intent = Intent(this, EditorActivity::class.java)
        intent.putExtra("KEY_IMAGE_URI", item.uri.toString())
        startActivity(intent)
    }

    // 权限请求启动器
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                loadImages()
            } else {
                Toast.makeText(this, "需要相册权限才能选择照片", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkPermissionsAndLoad()
    }

    private fun setupRecyclerView() {
        binding.rvGallery.layoutManager = GridLayoutManager(this, 3) // 3列网格
        binding.rvGallery.adapter = galleryAdapter
    }

    private fun checkPermissionsAndLoad() {
        // 根据 Android 版本决定请求哪个权限
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadImages()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadImages() {
        binding.progressBar.visibility = View.VISIBLE

        // 使用协程在后台线程加载数据，避免卡顿主线程
        lifecycleScope.launch(Dispatchers.IO) {
            val images = queryImages()

            // 切换回主线程更新 UI
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                galleryAdapter.submitList(images)
                if (images.isEmpty()) {
                    Toast.makeText(this@GalleryActivity, "相册是空的", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun queryImages(): List<MediaItem> {
        val imageList = mutableListOf<MediaItem>()

        // 查询字段
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        // 排序：最新的在前面
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val date = cursor.getLong(dateColumn)

                // 生成图片 URI
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                imageList.add(MediaItem(id, contentUri, name, date))
            }
        }
        return imageList
    }
}
