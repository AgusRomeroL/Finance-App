plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.gms.google-services")
}

android {
    namespace = "mx.budget"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.budget"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    buildFeatures {
        compose = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // ML Kit GenAI (genai-prompt beta) viene compilado con Kotlin 2.2 metadata;
        // el proyecto está en 2.0.21. Permite consumir su API sin subir el toolchain.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // WorkManager — pipeline de normalización/atribución retroactiva en background
    // (Apéndice F.3.4). Exento de las restricciones de background de Android 14.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    
    // Additional essential dependencies to pass compile resolution
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Preferencias persistidas (toggle de color dinámico)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Hilt just in case
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Wear OS Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Gemini Nano on-device: ML Kit GenAI Prompt API (vía GA, sin allowlist por-app;
    // maneja descarga del modelo con checkStatus()/download()). Reemplaza al SDK
    // experimental `aicore` (que daba PERMISSION_DENIED en el canal de terceros).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")

    // Compose Material 3 & Essentials
    implementation("androidx.compose.material3:material3")
    // Material 3 Adaptive — NavigationSuiteScaffold vive aquí (graduado en material3 1.3)
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    // SupportingPaneScaffold + PaneExpansionState (divisor de paneles arrastrable)
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")
    // WindowSizeClass para clasificar el ancho de ventana (foldable)
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    // MDC: solo se usa MaterialColors.harmonize (Blend.harmonize, cap ≤15° en HCT)
    // para armonizar los colores semánticos financieros hacia el primary dinámico.
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
