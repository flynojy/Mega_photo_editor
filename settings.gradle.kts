pluginManagement {
    repositories {
        // 1. 阿里云公共仓库 (最强聚合源)
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 2. 阿里云 Google 镜像
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // 3. 阿里云 Gradle 插件镜像
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // 4. 官方源保底
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 1. 阿里云公共仓库
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }

        // 2. 官方源
        google()
        mavenCentral()
    }
}

rootProject.name = "Mega_photo"
include(":app")