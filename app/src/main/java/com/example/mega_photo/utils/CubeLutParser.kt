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

    /**
     * 智能加载 LUT 数据 (对外唯一接口)
     */
    fun load(context: Context, assetFileName: String): CubeLutData? {
        // 生成缓存文件名，例如 "luts_KOTO.cube" -> "luts_KOTO.bin"
        val cacheFileName = assetFileName.replace("/", "_").replace(".cube", ".bin")
        val cacheFile = File(context.cacheDir, cacheFileName)

        // 1. 尝试从二进制缓存加载 (极快)
        if (cacheFile.exists()) {
            val cachedData = loadFromBinaryCache(cacheFile)
            if (cachedData != null) {
                return cachedData
            }
        }

        // 2. 缓存未命中，解析原始 Assets 文本文件 (较慢)
        val parsedData = parseFromAssets(context, assetFileName)

        // 3. 解析成功后，写入二进制缓存供下次使用
        if (parsedData != null) {
            saveToBinaryCache(cacheFile, parsedData)
        }

        return parsedData
    }

    private fun loadFromBinaryCache(file: File): CubeLutData? {
        try {
            FileInputStream(file).use { fis ->
                val channel = fis.channel
                // 内存映射文件，读取速度最快
                val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                buffer.order(ByteOrder.nativeOrder())

                // 读取头部 (Size)
                val size = buffer.int

                // 读取数据 (Float Array)
                val floatBuffer = buffer.asFloatBuffer()
                // slice() 是必须的，否则 buffer 位置可能会乱
                val data = floatBuffer.slice()

                return CubeLutData(size, data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache", e)
            file.delete() // 缓存损坏，删除之
            return null
        }
    }

    private fun saveToBinaryCache(file: File, data: CubeLutData) {
        try {
            FileOutputStream(file).use { fos ->
                val channel = fos.channel
                // 计算文件大小: 4字节(Size) + 数据点数 * 4字节(Float)
                val capacity = 4 + data.data.capacity() * 4
                val buffer = ByteBuffer.allocate(capacity).order(ByteOrder.nativeOrder())

                buffer.putInt(data.size)

                // 必须将源 buffer 位置重置才能读取
                data.data.position(0)
                buffer.asFloatBuffer().put(data.data)
                data.data.position(0) // 恢复位置供渲染器使用

                buffer.position(0)
                channel.write(buffer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache", e)
        }
    }

    // 原始的文本解析逻辑
    private fun parseFromAssets(context: Context, assetFileName: String): CubeLutData? {
        try {
            val inputStream = context.assets.open(assetFileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var size = -1
            val dataPoints = mutableListOf<Float>()

            var line: String? = reader.readLine()
            while (line != null) {
                line = line.trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    line = reader.readLine()
                    continue
                }

                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        size = parts[1].toInt()
                    }
                }
                else if (size > 0 && line.isNotEmpty()) {
                    val firstChar = line[0]
                    // 简单的数字检查
                    val isNumberStart = Character.isDigit(firstChar) || (firstChar == '-' && line.length > 1)

                    if (isNumberStart) {
                        val parts = line.split("\\s+".toRegex())
                        if (parts.size >= 3) {
                            dataPoints.add(parts[0].toFloat())
                            dataPoints.add(parts[1].toFloat())
                            dataPoints.add(parts[2].toFloat())
                        }
                    }
                }
                line = reader.readLine()
            }
            reader.close()

            if (size == -1 || dataPoints.isEmpty()) return null

            val buffer = ByteBuffer.allocateDirect(dataPoints.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()

            for (f in dataPoints) {
                buffer.put(f)
            }
            buffer.position(0)

            return CubeLutData(size, buffer)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
