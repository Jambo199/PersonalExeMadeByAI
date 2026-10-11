plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes the workflow run number so every published APK installs over the previous one.
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toIntOrNull() ?: 1

// Where the in-app updater looks for new builds (the rolling pre-release CI publishes).
val updateRepo = System.getenv("GITHUB_REPOSITORY") ?: "Jambo199/PersonalExeMadeByAI"
val updateBaseUrl = "https://github.com/$updateRepo/releases/download/vivid-music-latest/"

android {
    namespace = "com.jambo199.vividmusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jambo199.vividmusic"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "UPDATE_BASE_URL", "\"$updateBaseUrl\"")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // A fixed key keeps updates installable over older builds. Override it with your own
        // keystore through the SIGNING_* environment variables (see README).
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "vividmusic.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "vividmusic"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "vividmusic"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "vividmusic"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.hls)

    implementation(libs.androidx.palette.ktx)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    testImplementation(libs.junit)
    // The real org.json, so parser tests don't hit Android's stubs.
    testImplementation(libs.org.json)
}
