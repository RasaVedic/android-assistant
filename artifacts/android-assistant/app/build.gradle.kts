// app/build.gradle.kts
// Build config for the app module: SDK versions, dependencies, signing.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.assistant"
        minSdk = 26          // Android 8.0+ (covers ~95% of devices)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true   // Makes it easy to access views without findViewById
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.okhttp)               // HTTP client for Gemini API
    implementation(libs.gson)                 // JSON parsing for Gemini API
    implementation(libs.kotlinx.coroutines.android)  // For running network calls off the main thread
}
