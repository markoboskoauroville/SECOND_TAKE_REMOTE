val appVersion = (project.findProperty("appVersion") as String? ?: "1").trim().toInt()

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mantra.remoteshooting"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mantra.remoteshooting"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion
        versionName = appVersion.toString()
    }
    System.getenv("DEBUG_KEYSTORE")?.let { ks ->
        signingConfigs.getByName("debug") {
            storeFile = file(ks); storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
