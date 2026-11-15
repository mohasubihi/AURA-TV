plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.auratv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auratv.flow"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "6.0-flow"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
