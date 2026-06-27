plugins {
    id("com.android.application")
}

android {
    namespace = "com.smartisanos.textboom"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartisanos.textboom"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"
    }

    sourceSets.getByName("main") {
        manifest.srcFile("../AndroidManifest.xml")
        java.setSrcDirs(listOf("../src"))
        res.setSrcDirs(listOf("../res"))
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "../proguard.flags")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(files("../libs/android-support-v4.jar"))
    implementation(files("../libs/csopensdk.jar"))
}
