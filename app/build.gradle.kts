import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
keystoreProperties.load(FileInputStream(keystorePropertiesFile))

android {
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("AceKeystore") {
            keyAlias = System.getenv("KEY_ALIAS") ?: keystoreProperties["keyAlias"] as String
            keyPassword =
                System.getenv("KEY_PASSWORD") ?: keystoreProperties["keyPassword"] as String
            storeFile =
                if (System.getenv("STORE_FILE") != null && System.getenv("STORE_FILE") != "") file("../keystore.jks") else file(
                    keystoreProperties["storeFile"] as String
                )
            storePassword =
                System.getenv("STORE_PASSWORD") ?: keystoreProperties["storePassword"] as String
        }
    }

    defaultConfig {
        applicationId = "io.github.acedroidx.frp"
        minSdk = 23
        targetSdk = 37
        compileSdk = 37
        versionCode = 25
        versionName = "1.5.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        signingConfig = signingConfigs.getByName("AceKeystore")

        buildConfigField("String", "FrpVersion", "\"0.67.0\"")
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
            signingConfig = signingConfigs.getByName("AceKeystore")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("AceKeystore")
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
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    namespace = "io.github.acedroidx.frp"
}

val appVersionName = "1.5.7"

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiFilter = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
            val abi = abiFilter?.identifier ?: "universal"
            output.outputFileName.set("FRP_${abi}_${appVersionName}.apk")
        }
    }
}

configurations.all {
    exclude(group = "androidx.navigationevent", module = "navigationevent-compose")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
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
    implementation("androidx.navigationevent:navigationevent:1.1.0")

    // TOML parsing/serialization
    implementation("com.akuleshov7:ktoml-core:0.5.2")

    // Miuix theme (HyperOS style colors)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.0")

    // Tasker Plugin Library
    implementation("com.joaomgcd:taskerpluginlibrary:0.4.10")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
