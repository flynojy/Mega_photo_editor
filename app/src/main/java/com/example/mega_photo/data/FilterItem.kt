package com.example.mega_photo.data

data class FilterItem(
    val name: String,
    val lutFileName: String?,      // .cube 文件路径 (assets)
    val previewFileName: String?   // .jpg 预览图路径 (assets)
)