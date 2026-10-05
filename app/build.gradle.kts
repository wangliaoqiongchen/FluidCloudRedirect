import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名:根目录 keystore.properties 存在时启用(storeFile 相对 app/ 模块目录)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mo.fkLTY"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mo.fkLTY"
        minSdk = 29
        targetSdk = 37
        versionCode = 13
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // libxposed 新 API:框架进程内已有实现,必须 compileOnly,禁止打进 APK
    compileOnly(libs.libxposed.api)
    compileOnly(libs.libxposed.annotation)
    // 设置 App 端写远程偏好走 service 库(该库随 APK 打包)
    implementation(libs.libxposed.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// M6 验收:APK 内必须存在 META-INF/xposed 三件套(防 AGP 打包丢元数据)
val verifyXposedMetadata = tasks.register("verifyXposedMetadata") {
    group = "verification"
    description = "Asserts that META-INF/xposed/* module metadata survived APK packaging."
    val apkFile = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    doLast {
        val apk = apkFile.get().asFile
        if (!apk.exists()) throw GradleException("APK not found: $apk (run assembleDebug first)")
        ZipFile(apk).use { zip ->
            val required = listOf(
                "META-INF/xposed/java_init.list",
                "META-INF/xposed/module.prop",
                "META-INF/xposed/scope.list",
            )
            val missing = required.filter { zip.getEntry(it) == null }
            if (missing.isNotEmpty()) {
                throw GradleException("Xposed metadata missing from APK: $missing")
            }
        }
        println("Xposed metadata verified in ${apk.name}")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(verifyXposedMetadata)
}
