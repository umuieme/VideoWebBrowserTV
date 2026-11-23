plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.umuieme.videowebbrowser"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.umuieme.videowebbrowser"
        minSdk = 28
        // Deliberately below compileSdk: targetSdk 36+ turns on predictive back, which stops
        // KEYCODE_BACK reaching dispatchKeyEvent. See CLAUDE.md > Decisions.
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // targetSdk is intentionally held at 35 (see the comment on targetSdk above and
        // CLAUDE.md > Decisions). Re-enable this check when that decision is revisited.
        disable += "OldTargetApi"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    // Document-start script injection and a per-frame message channel for the media agent.
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
    // Real org.json for local unit tests: android.jar only has stubs that throw at runtime.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
