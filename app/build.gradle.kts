plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.auratv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auratv.lounge.v5"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "5.0-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
