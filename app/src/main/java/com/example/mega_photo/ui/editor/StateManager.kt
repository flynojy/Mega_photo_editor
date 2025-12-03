package com.example.mega_photo.ui.editor

import android.graphics.RectF
import com.example.mega_photo.data.FilterItem
import java.util.Stack

// 1. 定义编辑器的完整状态快照
data class EditorState(
    // 变换
    val scale: Float,
    val x: Float,
    val y: Float,
    val rotation: Float,
    val isFlipped: Boolean,
    // 裁剪
    val cropRect: RectF, // 这是一个副本
    // 调色
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    // 滤镜
    val filterItem: FilterItem
)

// 2. 状态管理器
class StateManager(private val maxHistory: Int = 10) { // 增加历史记录步数
    private val undoStack = Stack<EditorState>()
    private val redoStack = Stack<EditorState>()

    // 当前状态 (不在栈中，始终是最新状态)
    var currentState: EditorState? = null

    // 初始化/重置
    fun initialize(initialState: EditorState) {
        undoStack.clear()
        redoStack.clear()
        currentState = initialState
    }

    // 提交新状态 (每次操作完成时调用)
    fun commit(newState: EditorState) {
        // 如果新状态和当前状态一样，忽略
        if (currentState == newState) return

        currentState?.let {
            undoStack.push(it)
            // 限制栈大小
            if (undoStack.size > maxHistory) {
                undoStack.removeAt(0) // 移除最旧的
            }
        }
        currentState = newState
        redoStack.clear() // 一旦有新操作，重做历史失效
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(): EditorState? {
        if (undoStack.isEmpty()) return null

        val prevState = undoStack.pop()
        currentState?.let { redoStack.push(it) }
        currentState = prevState

        return prevState
    }

    fun redo(): EditorState? {
        if (redoStack.isEmpty()) return null

        val nextState = redoStack.pop()
        currentState?.let { undoStack.push(it) }
        currentState = nextState

        return nextState
    }
}