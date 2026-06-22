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
        versionCode = 2
        versionName = "0.2.0"
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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(files("../libs/android-support-v4.jar"))
    implementation(files("../libs/okhttp-2.7.5.jar"))
    implementation(files("../libs/okio-1.7.0.jar"))
    implementation(files("../libs/csopensdk.jar"))
}
