import java.io.File
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Assemble ONextBox's C16 stacked signal donor smali into assets/c16-stacked-signal.dex
val c16Smali by configurations.creating
dependencies {
    c16Smali("org.smali:smali:2.5.2")
}
val c16Assets = layout.buildDirectory.dir("generated/c16StackedSignal/assets")
val assembleC16StackedSignal by tasks.registering(JavaExec::class) {
    classpath = c16Smali
    mainClass.set("org.jf.smali.Main")
    inputs.dir("src/c16StackedSignal")
    outputs.file(c16Assets.map { it.file("c16-stacked-signal.dex") })
    doFirst {
        val output = c16Assets.get().file("c16-stacked-signal.dex").asFile
        output.parentFile.mkdirs()
        args = listOf("assemble", file("src/c16StackedSignal").absolutePath, "-o", output.absolutePath)
    }
}
tasks.named("preBuild").configure { dependsOn(assembleC16StackedSignal) }

// Detect if NDK & CMake are installed in Android SDK so native font bridge builds automatically when available
val sdkDirPath: String? = run {
    val lp = rootProject.file("local.properties")
    val fromProps = if (lp.isFile) {
        Properties().apply { lp.inputStream().use { load(it) } }.getProperty("sdk.dir")
    } else null
    fromProps ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
}
val hasNdkAndCmake = sdkDirPath != null &&
    File(sdkDirPath, "ndk").let { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) } &&
    File(sdkDirPath, "cmake").let { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }

android {
    namespace = "dev.nonlog.colorosnative"
    compileSdk = 37
    buildToolsVersion = "36.1.0"

    sourceSets.getByName("main").assets.srcDir(c16Assets.get().asFile)

    defaultConfig {
        applicationId = "dev.nonlog.colorosnative"
        minSdk = 31
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"

        if (hasNdkAndCmake) {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_STL=c++_static")
                }
            }
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    if (hasNdkAndCmake) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    compileOnly("io.github.libxposed:annotation:1.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
