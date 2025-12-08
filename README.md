本project是本人在byte dance工程训练营的结课作业
作为本人做的第一个app还是很感动的zzz
本身是一个带有一个简陋播放器的图片编辑器
让我认为为数不多有实用价值的是可以套用正常相机照片可以使用的.cube lut滤镜文件，或许可以让平时拍的人文风景好看一丢丢
示例图就是本人在Tokyo拍的skytree晴空塔，单就效果而言，我还是很高兴的
以下是ai生成的项目介绍

📸 Mega_photo - 基于 OpenGL ES 的专业级安卓修图应用

一个功能强大、界面简洁的 Android 图片与视频编辑工具。从相册选图、LUT 滤镜渲染到高清导出，一站式搞定。


✨ 项目简介

Mega_photo 是一个采用 Modern Android Development (MAD) 规范构建的图像处理应用。它不仅仅是一个图片查看器，更是一个拥有强大渲染引擎的修图工具。

核心亮点在于其 OpenGL ES 3.0 渲染管线，支持行业标准的 .cube 3D LUT 滤镜，实现了电影级的色彩映射。同时，应用还支持视频播放、GIF 预览以及丰富的手势编辑功能。

🚀 核心功能

1. 🎨 专业级 LUT 滤镜

3D Texture 技术：利用 OpenGL ES 3.0 的 3D 纹理特性，完美加载 .cube 格式滤镜。

高精度渲染：彻底解决了传统方案中的色彩断层、黑边和坐标映射错误，色彩还原度 100%。

实时预览：支持多种预设滤镜（Koto, Taipei, Greenland, Nightscape 等）实时切换。

2. 🖼️ 强大的图片编辑器

手势操作：支持双指缩放、单指平移，操作流畅跟手。

自由裁剪：提供带有半透明遮罩的交互式裁剪框，支持拖动调整裁剪区域。

旋转与翻转：支持左旋、右旋 90° 以及水平镜像翻转，并自动适配屏幕比例。

参数调节：支持 亮度、对比度、饱和度 的精细调节。

状态管理：内置 撤销 (Undo) 和 重做 (Redo) 功能，支持回溯 10 步操作。

3. 📂 媒体库与文件管理

相册浏览：基于 MediaStore API，高效加载设备上的图片。

多媒体预览：支持播放 MP4 视频（带进度条、快进/快退）和 GIF 动图。

高清导出：支持将编辑后的图片保存为 JPG (压缩) 或 PNG (无损) 格式到系统相册。

🛠️ 技术栈

语言: Kotlin

架构: MVVM (Model-View-ViewModel)

图形渲染: OpenGL ES 3.0, GLSL (着色器语言)

UI 组件: ViewBinding, RecyclerView, ConstraintLayout, CardView

图片加载: Glide

多媒体: VideoView, MediaStore API

构建工具: Gradle (Kotlin DSL)

📥 安装与运行

克隆项目

git clone [https://github.com/flynojy/MEGA_photo.git](https://github.com/flynojy/MEGA_photo.git)


导入 Android Studio

打开 Android Studio，选择 Open，选中项目根目录。

等待 Gradle Sync 完成。

运行

连接 Android 设备（需开启 USB 调试）或使用模拟器。

点击顶部绿色的 Run 按钮。

👤 开发者

姓名: (jynofly)

GitHub: flynojy

版本: v1.0.0

Made with ❤️ by jynofly
