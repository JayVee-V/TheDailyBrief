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
        versionCode = 23
        versionName = "0.8.2"
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
