plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Add this inside the android block
android {
    // ... other configurations ...

    buildFeatures {
        compose = true  // Enable Jetpack Compose
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3" // Use the latest stable version
    }

    // Ensure you have Kotlin 1.9.0 or higher
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// Add these dependencies
dependencies {
    // Jetpack Compose BOM (Bill of Materials) for version management
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Jetpack Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material Design for Compose
    implementation("androidx.compose.material3:material3")

    // Compose Activity
    implementation("androidx.activity:activity-compose:1.8.2")

    // Coroutines for Compose
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation Compose (if you need navigation)
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
