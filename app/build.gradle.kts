plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.mega_photo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mega_photo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0" // 版本号

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // [新增] 签名配置
    signingConfigs {
        create("release") {
            // 假设你的 keystore 文件名为 keystore.jks，放在 app 目录下
            storeFile = file("keystore.jks")
            storePassword = "你的密钥库密码" // 请替换为你设置的密码
            keyAlias = "key0"
            keyPassword = "你的密钥密码" // 请替换为你设置的密码
        }
    }

    buildTypes {
        release {
            // [修改] 引用上面的签名配置
            signingConfig = signingConfigs.getByName("release")

            // 开启代码混淆 (R8)
            isMinifyEnabled = true
            isShrinkResources = true // 移除无用资源
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.glide)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}