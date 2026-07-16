import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keyPropsFile = rootProject.file("key.properties")
val keyProps = Properties()
if (keyPropsFile.exists()) {
    keyProps.load(keyPropsFile.inputStream())
}

android {
    namespace = "com.cashewteam.novatext.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cashewteam.novatext.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 313
        versionName = "1.14.29"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    sourceSets.getByName("main") {
        manifest.srcFile("../AndroidManifest.xml")
        java.setSrcDirs(listOf("../src", "src/main/kotlin"))
        res.setSrcDirs(listOf("../res"))
    }

    signingConfigs {
        create("release") {
            if (keyPropsFile.exists()) {
                storeFile = file(keyProps["storeFile"] as String)
                storePassword = keyProps["storePassword"] as String
                keyAlias = keyProps["keyAlias"] as String
                keyPassword = keyProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../proguard.flags")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    ndkVersion = "26.1.10909125"

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation(files("libs/DeviceCompat-2.6.aar"))
    debugImplementation("androidx.compose.ui:ui-tooling")
}
