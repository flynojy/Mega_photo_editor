package com.example.mega_photo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object LutUtils {

    private const val TAG = "LutUtils"

    fun copyUriToInternalStorage(context: Context, uri: Uri, newFileName: String): File? {
        val destFile = File(getLutDir(context), newFileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file", e)
            return null
        }
    }

    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, fileName: String): String? {
        val destFile = File(getPreviewDir(context), fileName)
        try {
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // [新增] 删除导入的 LUT 及其关联文件
    fun deleteImportedLut(context: Context, lutPath: String, previewPath: String): Boolean {
        try {
            // 1. 删除 .cube 文件
            val lutFile = File(lutPath)
            if (lutFile.exists()) lutFile.delete()

            // 2. 删除 .jpg 预览图
            val previewFile = File(previewPath)
            if (previewFile.exists()) previewFile.delete()

            // 3. 删除二进制缓存 (.bin)
            // 这里的命名逻辑必须与 CubeLutParser.load 中的一致
            // 导入的文件名通常没有路径分隔符问题，直接取 name 即可
            val cacheKey = lutFile.name
            val cacheFileName = cacheKey.replace(".cube", ".bin")
            val cacheFile = File(context.cacheDir, cacheFileName)
            if (cacheFile.exists()) cacheFile.delete()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getLutDir(context: Context): File {
        val dir = File(context.filesDir, "imported_luts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPreviewDir(context: Context): File {
        val dir = File(context.filesDir, "imported_previews")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getImportedLuts(context: Context): List<Pair<String, String>> {
        val lutDir = getLutDir(context)
        val previewDir = getPreviewDir(context)
        val list = mutableListOf<Pair<String, String>>()

        if (lutDir.exists() && lutDir.isDirectory) {
            lutDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".cube", true)) {
                    val previewName = file.name.replace(".cube", ".jpg", true)
                    val previewFile = File(previewDir, previewName)
                    val previewPath = if (previewFile.exists()) previewFile.absolutePath else null

                    if (previewPath != null) {
                        list.add(Pair(file.absolutePath, previewPath))
                    }
                }
            }
        }
        return list
    }

    fun applyLutToBitmapCpu(data: CubeLutData, src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val scaledSrc = Bitmap.createScaledBitmap(src, 100, 100, true)
        val scaledW = scaledSrc.width
        val scaledH = scaledSrc.height

        val pixels = IntArray(scaledW * scaledH)
        scaledSrc.getPixels(pixels, 0, scaledW, 0, 0, scaledW, scaledH)

        val size = data.size
        val lutFloat = FloatArray(data.data.capacity())
        data.data.position(0)
        data.data.get(lutFloat)
        data.data.position(0)

        val sizeMinus1 = size - 1f

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF) / 255f
            val g = ((color shr 8) and 0xFF) / 255f
            val b = (color and 0xFF) / 255f

            val fr = r * sizeMinus1
            val fg = g * sizeMinus1
            val fb = b * sizeMinus1

            val ir = Math.round(fr).coerceIn(0, size - 1)
            val ig = Math.round(fg).coerceIn(0, size - 1)
            val ib = Math.round(fb).coerceIn(0, size - 1)

            val index = (ib * size * size + ig * size + ir) * 3

            if (index + 2 < lutFloat.size) {
                val newR = (lutFloat[index] * 255).toInt().coerceIn(0, 255)
                val newG = (lutFloat[index + 1] * 255).toInt().coerceIn(0, 255)
                val newB = (lutFloat[index + 2] * 255).toInt().coerceIn(0, 255)
                pixels[i] = Color.rgb(newR, newG, newB)
            }
        }

        return Bitmap.createBitmap(pixels, scaledW, scaledH, Bitmap.Config.ARGB_8888)
    }
}
