package com.example.mega_photo.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.example.mega_photo.R
import com.example.mega_photo.data.FilterItem
import com.example.mega_photo.utils.CubeLutData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.LinkedList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PhotoRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val TAG = "PhotoRenderer"
    private var programId: Int = 0
    private var baseBitmap: Bitmap? = null
    private var baseTextureId: Int = 0

    // LUT 3D 相关
    private var lut3DTextureId: Int = 0
    private var dummyLutTextureId: Int = 0
    private var hasLut: Boolean = false
    private var intensity: Float = 1.0f

    // [核心修复] 初始化时补全第3个参数 (null)
    private var currentFilterItem: FilterItem = FilterItem("Original", null, null)

    private var brightness: Float = 0.0f
    private var contrast: Float = 1.0f
    private var saturation: Float = 1.0f

    private var currentScale = 1.0f
    private var currentX = 0f
    private var currentY = 0f
    private var currentRotation = 0f
    private var isFlipped = false
    private val mvpMatrix = FloatArray(16)

    private var currentCropRect = RectF(0f, 0f, 1f, 1f)

    @Volatile
    private var savePathCallback: ((Bitmap) -> Unit)? = null

    private val runOnDraw = LinkedList<Runnable>()

    private var imageWidth = 0
    private var imageHeight = 0
    private var viewWidth = 0
    private var viewHeight = 0

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(80)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    fun setImage(bitmap: Bitmap) {
        baseBitmap = bitmap
        imageWidth = bitmap.width
        imageHeight = bitmap.height
        currentCropRect.set(0f, 0f, 1f, 1f)
    }

    fun setCubeLut(data: CubeLutData?) {
        runOnDraw.add(Runnable {
            if (data == null) {
                hasLut = false
            } else {
                lut3DTextureId = loadTexture3D(data)
                hasLut = true
            }
        })
    }

    fun updateFilterRecord(item: FilterItem) {
        currentFilterItem = item
    }

    fun setFilterIntensity(value: Float) {
        intensity = value
    }

    fun setAdjustments(br: Float, ct: Float, sa: Float) {
        brightness = br
        contrast = ct
        saturation = sa
    }

    fun saveImage(callback: (Bitmap) -> Unit) {
        savePathCallback = callback
    }

    fun updateTransform(dx: Float, dy: Float, scaleFactor: Float) {
        currentScale *= scaleFactor
        currentScale = currentScale.coerceIn(0.5f, 5.0f)
        currentX += dx
        currentY += dy
    }

    fun rotateLeft() {
        runOnDraw.add(Runnable {
            currentRotation += 90f
            currentRotation %= 360f
            adjustAspectRatio()
        })
    }

    fun rotateRight() {
        runOnDraw.add(Runnable {
            currentRotation -= 90f
            currentRotation %= 360f
            adjustAspectRatio()
        })
    }

    fun flipHorizontal() {
        runOnDraw.add(Runnable {
            isFlipped = !isFlipped
        })
    }

    fun getCurrentState(): EditorState {
        return EditorState(
            scale = currentScale,
            x = currentX,
            y = currentY,
            rotation = currentRotation,
            isFlipped = isFlipped,
            cropRect = RectF(currentCropRect),
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            filterItem = currentFilterItem
        )
    }

    fun restoreState(state: EditorState) {
        runOnDraw.add(Runnable {
            currentScale = state.scale
            currentX = state.x
            currentY = state.y
            currentRotation = state.rotation
            isFlipped = state.isFlipped
            currentCropRect.set(state.cropRect)
            brightness = state.brightness
            contrast = state.contrast
            saturation = state.saturation
            adjustAspectRatio()
        })
    }

    fun applyCrop(viewRect: RectF, onResult: (Int, Int) -> Unit) {
        runOnDraw.add(Runnable {
            val modelMatrix = FloatArray(16)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, currentX, currentY, 0f)
            Matrix.rotateM(modelMatrix, 0, currentRotation, 0f, 0f, 1f)
            val flipScale = if (isFlipped) -1.0f else 1.0f
            Matrix.scaleM(modelMatrix, 0, currentScale * flipScale, currentScale, 1.0f)

            val invertedMatrix = FloatArray(16)
            if (!Matrix.invertM(invertedMatrix, 0, modelMatrix, 0)) {
                return@Runnable
            }

            val corners = floatArrayOf(
                viewRect.left * 2 - 1, 1 - viewRect.top * 2, 0f, 1f,
                viewRect.right * 2 - 1, 1 - viewRect.top * 2, 0f, 1f,
                viewRect.left * 2 - 1, 1 - viewRect.bottom * 2, 0f, 1f,
                viewRect.right * 2 - 1, 1 - viewRect.bottom * 2, 0f, 1f
            )

            val mappedCorners = FloatArray(16)
            for (i in 0 until 4) {
                Matrix.multiplyMV(mappedCorners, i * 4, invertedMatrix, 0, corners, i * 4)
            }

            val (modelScaleX, modelScaleY) = calculateModelScale()
            var minU = 1f; var maxU = 0f; var minV = 1f; var maxV = 0f

            for (i in 0 until 4) {
                val lx = mappedCorners[i * 4]
                val ly = mappedCorners[i * 4 + 1]
                val u = (lx / modelScaleX) * 0.5f + 0.5f
                val v = 0.5f - (ly / modelScaleY) * 0.5f
                minU = min(minU, u); maxU = max(maxU, u); minV = min(minV, v); maxV = max(maxV, v)
            }
            minU = minU.coerceIn(0f, 1f); maxU = maxU.coerceIn(0f, 1f); minV = minV.coerceIn(0f, 1f); maxV = maxV.coerceIn(0f, 1f)

            val oldW = currentCropRect.width(); val oldH = currentCropRect.height()
            val finalMinU = currentCropRect.left + oldW * minU
            val finalMaxU = currentCropRect.left + oldW * maxU
            val finalMinV = currentCropRect.top + oldH * minV
            val finalMaxV = currentCropRect.top + oldH * maxV
            currentCropRect.set(finalMinU, finalMinV, finalMaxU, finalMaxV)

            currentScale = 1.0f; currentX = 0f; currentY = 0f; currentRotation = 0f; isFlipped = false
            adjustAspectRatio()

            val newPixelW = (imageWidth * currentCropRect.width()).toInt()
            val newPixelH = (imageHeight * currentCropRect.height()).toInt()
            onResult(newPixelW, newPixelH)
        })
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        val vertexCode = readShader(R.raw.vertex_shader)
        val fragmentCode = readShader(R.raw.fragment_shader_cube)
        programId = createProgram(vertexCode, fragmentCode)
        if (programId == 0) return
        GLES30.glUseProgram(programId)
        baseBitmap?.let { baseTextureId = loadTexture(it) }
        dummyLutTextureId = createDummyTexture3D()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewWidth = width; viewHeight = height
        adjustAspectRatio()
    }

    private fun calculateModelScale(): Pair<Float, Float> {
        if (viewWidth == 0 || viewHeight == 0 || imageWidth == 0 || imageHeight == 0) return Pair(1f, 1f)
        val isRotated = (abs(currentRotation) % 180f) == 90f
        val cropW = imageWidth * currentCropRect.width()
        val cropH = imageHeight * currentCropRect.height()
        val imgRatio = if (isRotated) cropH / cropW else cropW / cropH
        val viewRatio = viewWidth.toFloat() / viewHeight
        var screenScaleX = 1f; var screenScaleY = 1f
        if (imgRatio > viewRatio) screenScaleY = viewRatio / imgRatio else screenScaleX = imgRatio / viewRatio
        val modelScaleX = if (isRotated) screenScaleY else screenScaleX
        val modelScaleY = if (isRotated) screenScaleX else screenScaleY
        return Pair(modelScaleX, modelScaleY)
    }

    private fun adjustAspectRatio() {
        if (viewWidth == 0 || viewHeight == 0 || imageWidth == 0 || imageHeight == 0) return
        val effectiveImgW = imageWidth * currentCropRect.width()
        val effectiveImgH = imageHeight * currentCropRect.height()
        val isRotated = (abs(currentRotation) % 180f) == 90f
        val imgRatio = if (isRotated) effectiveImgH / effectiveImgW else effectiveImgW / effectiveImgH
        val viewRatio = viewWidth.toFloat() / viewHeight
        var screenScaleX = 1f; var screenScaleY = 1f
        if (imgRatio > viewRatio) screenScaleY = viewRatio / imgRatio else screenScaleX = imgRatio / viewRatio
        val modelScaleX = if (isRotated) screenScaleY else screenScaleX
        val modelScaleY = if (isRotated) screenScaleX else screenScaleY
        val uL = currentCropRect.left; val uR = currentCropRect.right; val vT = currentCropRect.top; val vB = currentCropRect.bottom
        val vertexData = floatArrayOf(
            -modelScaleX, -modelScaleY, 0f, uL, vB,
            modelScaleX, -modelScaleY, 0f, uR, vB,
            -modelScaleX,  modelScaleY, 0f, uL, vT,
            modelScaleX,  modelScaleY, 0f, uR, vT
        )
        vertexBuffer.clear(); vertexBuffer.put(vertexData); vertexBuffer.position(0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (programId == 0) return
        while (!runOnDraw.isEmpty()) { runOnDraw.poll()?.run() }
        GLES30.glUseProgram(programId)

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.translateM(mvpMatrix, 0, currentX, currentY, 0f)
        Matrix.rotateM(mvpMatrix, 0, currentRotation, 0f, 0f, 1f)
        val flipScale = if (isFlipped) -1.0f else 1.0f
        Matrix.scaleM(mvpMatrix, 0, currentScale * flipScale, currentScale, 1.0f)

        val uMVPMatrixHandle = GLES30.glGetUniformLocation(programId, "uMVPMatrix")
        GLES30.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)

        val stride = 5 * 4
        vertexBuffer.position(0)
        val aPositionHandle = GLES30.glGetAttribLocation(programId, "aPosition")
        GLES30.glEnableVertexAttribArray(aPositionHandle)
        GLES30.glVertexAttribPointer(aPositionHandle, 3, GLES30.GL_FLOAT, false, stride, vertexBuffer)
        vertexBuffer.position(3)
        val aTexCoordHandle = GLES30.glGetAttribLocation(programId, "aTexCoord")
        GLES30.glEnableVertexAttribArray(aTexCoordHandle)
        GLES30.glVertexAttribPointer(aTexCoordHandle, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, baseTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uTexture"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uLutTexture"), 1)

        if (hasLut) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lut3DTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uHasLut"), 1)
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, dummyLutTextureId)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programId, "uHasLut"), 0)
        }

        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uIntensity"), intensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uBrightness"), brightness)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uContrast"), contrast)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programId, "uSaturation"), saturation)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        savePathCallback?.let { callback ->
            val bitmap = createBitmapFromGLSurface(0, 0, viewWidth, viewHeight)
            callback(bitmap)
            savePathCallback = null
        }
    }

    private fun createBitmapFromGLSurface(x: Int, y: Int, w: Int, h: Int): Bitmap {
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = java.nio.IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)
        GLES30.glReadPixels(x, y, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, intBuffer)
        var offset1: Int; var offset2: Int
        for (i in 0 until h) {
            offset1 = i * w; offset2 = (h - i - 1) * w
            for (j in 0 until w) {
                val texturePixel = bitmapBuffer[offset1 + j]
                val blue = (texturePixel shr 16) and 0xff
                val red = (texturePixel shl 16) and 0x00ff0000
                val pixel = (texturePixel and -0xff0100) or red or blue
                bitmapSource[offset2 + j] = pixel
            }
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun createDummyTexture3D(): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        val buffer = ByteBuffer.allocateDirect(3)
        buffer.put(255.toByte()).put(255.toByte()).put(255.toByte())
        buffer.position(0)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB, 1, 1, 1, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, buffer)
        return textureIds[0]
    }

    private fun loadTexture3D(lutData: CubeLutData): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, textureIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB, lutData.size, lutData.size, lutData.size, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, lutData.data)
        return textureIds[0]
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        return textureIds[0]
    }

    private fun readShader(resId: Int): String {
        return context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexCode)
        if (vertexShader == 0) return 0
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentCode)
        if (fragmentShader == 0) return 0
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Link error: " + GLES30.glGetProgramInfoLog(program))
            GLES30.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Compile error: " + GLES30.glGetShaderInfoLog(shader))
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
