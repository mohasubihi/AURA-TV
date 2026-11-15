plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.auratv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auratv.cinema"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "8.0-cinema"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
