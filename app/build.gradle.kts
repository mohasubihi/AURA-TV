plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.auratv.launcher"
    compileSdk = 35

    defaultConfig {
        // New ID intentionally allows side-by-side testing with AURA TV 2.0.
        applicationId = "com.auratv.studio"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "3.0-beta"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
