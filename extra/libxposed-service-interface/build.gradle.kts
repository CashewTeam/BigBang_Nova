plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.libxposed.service.interface_"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    buildFeatures {
        aidl = true
        buildConfig = false
        resValues = false
    }
}
