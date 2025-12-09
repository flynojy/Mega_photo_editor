📸 MEGA_photo - 专业级安卓修图 App

![logo](https://github.com/user-attachments/assets/ae65371c-429f-43d6-a560-cbc3cdda4821)

一个功能强大、界面简洁的 Android 图片与视频编辑工具。从相册选图、电影级 LUT 滤镜渲染到高清导出，一站式搞定。

✨ 项目简介

MEGA_photo 是一个采用 Modern Android Development (MAD) 规范构建的图像处理应用。它不仅仅是一个图片查看器，更是一个拥有强大 OpenGL 渲染引擎的修图工具。

核心亮点在于其自主搭建的 OpenGL ES 3.0 渲染管线，支持行业标准的 .cube 3D LUT 滤镜，实现了电影级的色彩映射。同时，应用还支持视频播放、GIF 预览以及丰富的手势编辑功能。

🚀 核心功能展示

![ui](https://github.com/user-attachments/assets/16923a6c-b95c-46a6-b4c9-1b1fd14e2c9c)


1. 🎨 电影级 LUT 滤镜引擎

3D Texture 技术：利用 OpenGL ES 3.0 的 3D 纹理特性，完美加载 .cube 格式滤镜。

高精度渲染：彻底解决了传统方案中的色彩断层、黑边和坐标映射错误，色彩还原度 100%。

实时预览：支持多种预设滤镜（Koto, Taipei, Greenland, Nightscape 等）实时切换。

自定义导入：支持用户从手机存储导入自定义 .cube 文件，并自动生成预览缩略图。

<img width="861" height="1156" alt="image" src="https://github.com/user-attachments/assets/5cd63ba2-c304-4348-910d-e1585dd1967b" />


2. 🖼️ 强大的图片编辑器

交互式裁剪：提供带有半透明遮罩的裁剪框，支持拖动角点或整体移动，裁剪后自动放大适配。

手势操作：支持双指缩放、单指平移，操作流畅跟手。

旋转与翻转：支持左旋、右旋 90° 以及水平镜像翻转，并自动智能适配屏幕比例。

参数调节：支持 亮度、对比度、饱和度 的精细调节。

撤销/重做：内置状态管理器，支持 10 步历史记录回溯，编辑无忧。

![cut_demo](https://github.com/user-attachments/assets/f78ec73f-0711-4c95-ab6b-eb49940c16e7)


3. 📂 媒体库与文件管理

相册浏览：基于 MediaStore API，高效加载设备上的图片。

多媒体预览：支持播放 MP4 视频（带进度条、快进/快退）和 GIF 动图。

高清导出：支持将编辑后的图片保存为 JPG (压缩) 或 PNG (无损) 格式到系统相册。

4. 💎 精致的 UI/UX

沉浸式开屏：酷炫的 "MEGA" 跳跃动画 + 白色光感过渡。

圆形入口：首页采用大气的圆形卡片设计，配合由慢到快的扩散转场动画。

暗色模式：编辑器采用专业的深色背景，专注于内容创作。

🛠️ 技术栈

语言: Kotlin

架构: MVVM (Model-View-ViewModel)

图形渲染: OpenGL ES 3.0, GLSL (着色器语言)

UI 组件: ViewBinding, RecyclerView, ConstraintLayout, CardView

图片加载: Glide

多媒体: VideoView, MediaStore API

构建工具: Gradle (Kotlin DSL)

🏗️ 构建与运行说明

环境要求

Android Studio: 最新版本 (推荐)

JDK: 17+

Android SDK: API Level 34 (UpsideDownCake)

Min SDK: API Level 24 (Android 7.0)

快速开始

克隆项目

git clone https://github.com/flynojy/MEGA_photo.git


导入项目

打开 Android Studio，选择 Open，然后选择项目根目录。

等待 Gradle Sync 完成。

运行应用

连接一台开启了 开发者模式 和 USB 调试 的 Android 手机。

点击 Android Studio 顶部的绿色 Run 按钮 (三角形图标)。

生成安装包 (APK/AAB)

如果你需要生成可分发的安装包：

在 Android Studio 终端 (Terminal) 运行以下命令：

生成 APK: ./gradlew assembleRelease

生成 AAB: ./gradlew bundleRelease

生成的 APK 文件位于：app/build/outputs/apk/release/app-release.apk

👤 开发者

姓名: 王鹏 (jynofly)

GitHub: flynojy

版本: v1.2.0

Made with ❤️ by jynofly
