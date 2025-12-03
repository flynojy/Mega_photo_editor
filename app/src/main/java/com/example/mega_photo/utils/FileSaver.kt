package com.example.mega_photo.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

object FileSaver {

    enum class Format { JPG, PNG }

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        format: Format = Format.JPG
    ): Uri? {
        val filename = "MEGA_${System.currentTimeMillis()}"
        val extension = if (format == Format.PNG) "png" else "jpg"
        val mimeType = if (format == Format.PNG) "image/png" else "image/jpeg"
        val compressFormat = if (format == Format.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = 100

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MegaPhoto")
                put(MediaStore.MediaColumns.IS_PENDING, 1) // 标记为正在写入
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            var stream: OutputStream? = null
            try {
                stream = resolver.openOutputStream(it)
                if (stream != null) {
                    bitmap.compress(compressFormat, quality, stream)
                }

                // 写入完成，解除 Pending 状态
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            } finally {
                stream?.close()
            }
        }
        return uri
    }
}
