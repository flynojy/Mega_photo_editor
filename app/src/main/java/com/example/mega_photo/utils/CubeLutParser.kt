package com.example.mega_photo.utils

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

data class CubeLutData(
    val size: Int,
    val data: FloatBuffer
)

object CubeLutParser {

    private const val TAG = "CubeLutParser"

    fun load(context: Context, assetFileName: String): CubeLutData? {
        // 兼容外部文件路径 (如果 path 包含 /storage 或 /data，说明是绝对路径，不是 assets)
        val isAsset = !assetFileName.startsWith("/") && !assetFileName.startsWith("content://")

        // 缓存文件名处理：如果是绝对路径，取文件名部分
        val cacheKey = if (isAsset) assetFileName else File(assetFileName).name
        val cacheFileName = cacheKey.replace("/", "_").replace(".cube", ".bin")
        val cacheFile = File(context.cacheDir, cacheFileName)

        if (cacheFile.exists()) {
            val cachedData = loadFromBinaryCache(cacheFile)
            if (cachedData != null) return cachedData
        }

        val parsedData = if (isAsset) {
            parseFromAssets(context, assetFileName)
        } else {
            parseFromFile(assetFileName)
        }

        if (parsedData != null) {
            saveToBinaryCache(cacheFile, parsedData)
        }

        return parsedData
    }

    private fun loadFromBinaryCache(file: File): CubeLutData? {
        try {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                if (channel.size() < 4) return null
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                buffer.order(ByteOrder.nativeOrder())
                val size = buffer.int
                val floatBuffer = buffer.asFloatBuffer()
                val data = floatBuffer.slice()
                return CubeLutData(size, data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache", e)
            file.delete()
            return null
        }
    }

    private fun saveToBinaryCache(file: File, data: CubeLutData) {
        try {
            FileOutputStream(file).use { fos ->
                val channel = fos.channel
                val capacity = 4 + data.data.capacity() * 4
                val buffer = ByteBuffer.allocate(capacity).order(ByteOrder.nativeOrder())
                buffer.putInt(data.size)
                data.data.position(0)
                buffer.asFloatBuffer().put(data.data)
                data.data.position(0)
                buffer.position(0)
                channel.write(buffer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache", e)
        }
    }

    private fun parseFromAssets(context: Context, assetFileName: String): CubeLutData? {
        return try {
            val inputStream = context.assets.open(assetFileName)
            parseStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Asset parse error", e)
            null
        }
    }

    private fun parseFromFile(filePath: String): CubeLutData? {
        return try {
            val inputStream = FileInputStream(filePath)
            parseStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "File parse error", e)
            null
        }
    }

    // [核心修复] 通用解析逻辑，增强兼容性
    private fun parseStream(inputStream: java.io.InputStream): CubeLutData? {
        val reader = BufferedReader(InputStreamReader(inputStream))
        var size = -1
        val dataPoints = mutableListOf<Float>()

        try {
            var line: String? = reader.readLine()
            while (line != null) {
                line = line.trim()

                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    line = reader.readLine()
                    continue
                }

                // 解析 TITLE (跳过)
                if (line.startsWith("TITLE")) {
                    line = reader.readLine()
                    continue
                }

                // 解析尺寸
                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        size = parts[1].toInt()
                    }
                }
                // 解析数据
                else {
                    // 检查行首字符是否可能是数字 (数字、负号、点)
                    val firstChar = line[0]
                    val isPotentiallyNumber = Character.isDigit(firstChar) || firstChar == '-' || firstChar == '.'

                    if (isPotentiallyNumber && size > 0) {
                        // 使用正则分割，兼容空格和 Tab
                        val parts = line.split("\\s+".toRegex())
                        // 有些行可能包含 3个数字，有些可能更多或更少，只要能凑齐 RGB 就行
                        // 通常是 "R G B"
                        if (parts.size >= 3) {
                            try {
                                val r = parts[0].toFloat()
                                val g = parts[1].toFloat()
                                val b = parts[2].toFloat()
                                dataPoints.add(r)
                                dataPoints.add(g)
                                dataPoints.add(b)
                            } catch (e: NumberFormatException) {
                                // 忽略解析失败的行 (可能是非数据行)
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
        } finally {
            reader.close()
        }

        if (size == -1 || dataPoints.isEmpty()) {
            Log.e(TAG, "Invalid CUBE file: size=$size, points=${dataPoints.size}")
            return null
        }

        // 验证数据完整性 (size^3 * 3)
        val expectedSize = size * size * size * 3
        if (dataPoints.size != expectedSize) {
            Log.w(TAG, "Data points mismatch! Expected: $expectedSize, Actual: ${dataPoints.size}. Attempting to use anyway.")
            // 某些 LUT 可能不完整，但我们还是尝试转换
        }

        val buffer = ByteBuffer.allocateDirect(dataPoints.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (f in dataPoints) {
            buffer.put(f)
        }
        buffer.position(0)

        return CubeLutData(size, buffer)
    }
}