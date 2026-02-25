// app/build.gradle.kts

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Required for Firebase — apply AFTER android and kotlin plugins
    id("com.google.gms.google-services")
}

android {
    namespace = "com.findfriends"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.findfriends"
        minSdk = 26          // API 26 = Android 8.0. BLE advertising reliable from here.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("com.google.android.material:material:1.11.0")

    // ── Compose BOM — keeps all Compose versions in sync ──────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")

    // ── Lifecycle + ViewModel ──────────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // ── Coroutines ─────────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // ^ Required for .await() on Firebase Tasks

    // ── Firebase BOM — keeps all Firebase versions in sync ────────────────────
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    // ^ Auth needed for Firestore security rules (anonymous sign-in)

    // ── Accompanist permissions (for RequireBluetoothPermissions composable) ───
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // ── Debug only ────────────────────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// ─── Root build.gradle.kts additions (project level) ─────────────────────────
// Make sure your PROJECT-level build.gradle.kts has:
//
//   plugins {
//       id("com.google.gms.google-services") version "4.4.1" apply false
//   }
//
// And your settings.gradle.kts includes:
//   dependencyResolutionManagement {
//       repositories {
//           google()
//           mavenCentral()
//       }

//   }