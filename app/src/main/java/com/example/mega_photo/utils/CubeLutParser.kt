package com.example.mega_photo.utils

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class CubeLutData(
    val size: Int,
    val data: FloatBuffer
)

object CubeLutParser {

    fun parse(context: Context, assetFileName: String): CubeLutData? {
        try {
            val inputStream = context.assets.open(assetFileName)
            val reader = BufferedReader(InputStreamReader(inputStream))

            var size = -1
            val dataPoints = mutableListOf<Float>()

            var line: String? = reader.readLine()
            while (line != null) {
                line = line.trim()
                // 跳过注释和空行
                if (line.isEmpty() || line.startsWith("#")) {
                    line = reader.readLine()
                    continue
                }

                // 解析尺寸关键字：LUT_3D_SIZE 33
                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        size = parts[1].toInt()
                    }
                }
                // 解析数据行：0.000000 0.000000 0.000000
                // 简单的启发式检查：确保行以数字或负号开头
                else if (size > 0 && line.isNotEmpty()) {
                    val firstChar = line[0]
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

            // 转换为 FloatBuffer (native order)
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