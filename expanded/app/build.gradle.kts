plugins {
    id("com.android.application")
}

android {
    namespace = "org.morningbrief.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.morningbrief.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "0.8.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
