plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.7"

        // Expose version info to Kotlin code
        buildConfigField("String", "VERSION_CHECK_URL",
            "\"https://raw.githubusercontent.com/RasaVedic/android-assistant/HEAD/artifacts/android-assistant/version.json\"")
        buildConfigField("String", "DOWNLOAD_PAGE_URL",
            "\"https://github.com/RasaVedic/android-assistant/releases/latest\"")

        // API keys baked in at build time from GitHub Secrets (set GEMINI_API_KEY secret in repo)
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${System.getenv("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "FIREBASE_API_KEY",
            "\"${System.getenv("FIREBASE_API_KEY") ?: ""}\"")
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

    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures {
        viewBinding = true
        buildConfig = true   // Required to access BuildConfig in Kotlin code
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
}
