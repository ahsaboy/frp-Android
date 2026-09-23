import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}
// 没有签名配置（新 clone / 无签名环境）时不创建 signingConfig，避免配置阶段崩溃
val envStoreFile = System.getenv("STORE_FILE")
val hasSigning = (!envStoreFile.isNullOrEmpty()) ||
    keystoreProperties.getProperty("storeFile") != null

// frp 版本统一由 gradle.properties 维护（./gradlew updateFrp 升级到官方最新版）
val frpVersion: String = (project.findProperty("frpVersion") as? String)
    ?: error("gradle.properties 中缺少 frpVersion")

val appVersionName = "1.5.12"

android {
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasSigning) {
            create("AceKeystore") {
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: keystoreProperties.getProperty("keyAlias") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: keystoreProperties.getProperty("keyPassword") ?: ""
                storeFile = if (!envStoreFile.isNullOrEmpty()) file("../keystore.jks")
                else file(keystoreProperties.getProperty("storeFile")!!)
                storePassword = System.getenv("STORE_PASSWORD")
                    ?: keystoreProperties.getProperty("storePassword") ?: ""
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.acedroidx.frp"
        minSdk = 24
        targetSdk = 37
        compileSdk = 37
        versionCode = 29
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FrpVersion", "\"$frpVersion\"")
        buildConfigField("String", "FrpcFileName", "\"libfrpc.so\"")
        buildConfigField("String", "FrpsFileName", "\"libfrps.so\"")
        buildConfigField("String", "FrpcConfigFileName", "\"frpc.toml\"")
        buildConfigField("String", "FrpsConfigFileName", "\"frps.toml\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                // Includes the default ProGuard rules files that are packaged with
                // the Android Gradle plugin. To learn more, go to the section about
                // R8 configuration files.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                // Includes a local, custom Proguard rules file
                "proguard-rules.pro"
            )
            if (hasSigning) signingConfig = signingConfigs.getByName("AceKeystore")
        }
        getByName("debug") {
            if (hasSigning) signingConfig = signingConfigs.getByName("AceKeystore")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    namespace = "io.github.acedroidx.frp"
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
            val abi = abiFilter?.identifier ?: "arm64-v8a"
            output.outputFileName.set("FRP_${abi}_${appVersionName}.apk")
        }
    }
}

configurations.all {
    exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Integration with activities
    implementation("androidx.activity:activity-compose:1.13.0")
    // NavigationEvent base library (compose bindings are patched locally for miuix compatibility)
    implementation("androidx.navigationevent:navigationevent:1.1.2")

    // TOML parsing/serialization
    implementation("com.akuleshov7:ktoml-core:0.7.1")

    // Miuix theme (HyperOS style colors)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4")

    // Tasker Plugin Library
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// ===== frp 官方二进制同步 =====
// 版本锁定在 gradle.properties 的 frpVersion；构建前自动下载官方 android_arm64 二进制
val frpCacheDir = rootProject.layout.projectDirectory.dir("gradle/.frp-cache").asFile
val frpTarName = "frp_${frpVersion}_android_arm64.tar.gz"
val frpTarFile = File(frpCacheDir, frpTarName)
val frpExtractDir = File(layout.buildDirectory.get().asFile, "frp-extract/$frpVersion")
val jniLibsArm64 = File(layout.projectDirectory.asFile, "src/main/jniLibs/arm64-v8a")

val downloadFrp = tasks.register("downloadFrp") {
    group = "frp"
    description = "下载锁定版本的 frp 官方二进制到本地缓存"
    // 局部化：doLast 不能捕获脚本对象引用（配置缓存要求）
    val tarFile = frpTarFile
    val tarName = frpTarName
    val version = frpVersion
    outputs.file(tarFile)
    outputs.upToDateWhen { tarFile.exists() }
    doLast {
        if (tarFile.exists()) {
            logger.lifecycle("Using cached frp archive: $tarFile")
            return@doLast
        }
        tarFile.parentFile.mkdirs()
        val url =
            "https://github.com/fatedier/frp/releases/download/v$version/$tarName"
        logger.lifecycle("Downloading frp v$version: $url")
        val part = File(tarFile.parentFile, "$tarName.part")
        URL(url).openStream().use { input ->
            part.outputStream().use { output -> input.copyTo(output) }
        }
        if (!part.renameTo(tarFile)) error("下载缓存重命名失败: $part")
    }
}

val extractFrp = tasks.register<Copy>("extractFrp") {
    group = "frp"
    description = "解压 frpc/frps"
    dependsOn(downloadFrp)
    from(tarTree(resources.gzip(frpTarFile)))
    include("**/frpc", "**/frps")
    into(frpExtractDir)
}

val installFrp = tasks.register("installFrp") {
    group = "frp"
    description = "安装 frpc/frps 到 jniLibs"
    // 局部化：doLast 不能捕获脚本对象引用（配置缓存要求）
    val extractDir = frpExtractDir
    val libDir = jniLibsArm64
    dependsOn(extractFrp)
    inputs.dir(extractDir)
    outputs.files(File(libDir, "libfrpc.so"), File(libDir, "libfrps.so"))
    doLast {
        libDir.mkdirs()
        extractDir.walkTopDown()
            .filter { it.isFile && (it.name == "frpc" || it.name == "frps") }
            .forEach { src ->
                val target = File(libDir, "lib${src.name}.so")
                src.copyTo(target, overwrite = true)
                target.setExecutable(true, false)
                logger.lifecycle("Installed ${target.name}")
            }
        check(File(libDir, "libfrpc.so").exists() && File(libDir, "libfrps.so").exists()) {
            "frp 二进制解压结果不完整"
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(installFrp) }
