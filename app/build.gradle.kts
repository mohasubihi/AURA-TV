plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.auratv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auratv.nova"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "7.0-nova"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
