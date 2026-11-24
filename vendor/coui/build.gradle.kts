// COUI-Kit 0.0.1-alpha18 的本地源码副本（vendor），只为本 fork 保留一处补丁：
// 触点菜单（CouiMenuAnchor）纵向不再压住触发控件，见
// src/main/java/com/itos/coui/menu/Menu.kt 与 CouiMenuMotion.kt 的 avoidAnchorGap。
// 未复制上游 navigation3 包：该组件依赖 androidx.navigation3:navigation3-ui 1.2.1，
// 而本 fork 的导航栈是 miuix-nav（top.yukonga.miuix.kmp.nav），用不上，也不引入该依赖。
// 升级步骤：把上游 tag 的 coui/src 覆盖过来（排除 navigation3/），再重新应用这处改动。
plugins {
    // 版本由根工程的 classpath 提供，这里只声明插件 id，避免与 AGP 的未知版本冲突。
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.itos.coui"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        minSdk = 31
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
    }
}

// 依赖与上游 coui/build.gradle.kts 保持一致（版本取自 COUI-Kit 0.0.1-alpha16 的 version catalog）。
dependencies {
    api("androidx.compose.ui:ui:1.13.0")
    api("androidx.compose.animation:animation:1.13.0")
    api("androidx.compose.foundation:foundation:1.13.0")
    implementation("androidx.compose.runtime:runtime:1.13.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("androidx.core:core-ktx:1.19.1")
}
