plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ua.vadovsky.cryptowatcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "ua.vadovsky.cryptowatcher"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }
}
