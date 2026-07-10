plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("com.google.gms.google-services")
}

android {
    namespace = "mx.budget"
    compileSdk = 36

    defaultConfig {
        applicationId = "mx.budget"
        minSdk = 31
        targetSdk = 36
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
}

// Con el toolchain en Kotlin 2.2 la metadata de ML Kit GenAI/LiteRT-LM ya es
// nativa: se quitó -Xskip-metadata-version-check (y se reactivó el incremental
// en gradle.properties).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Constantes compartidas del Data Layer telefono-reloj (MVP Fase 5).
    implementation(project(":wearcore"))

    // Tests JVM puros (src/test): parser NL determinista sin deps de Android.
    testImplementation("junit:junit:4.13.2")

    val room_version = "2.7.2"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // WorkManager — pipeline de normalización/atribución retroactiva en background
    // (Apéndice F.3.4). Exento de las restricciones de background de Android 14.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
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
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-compiler:2.57")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Wear OS Data Layer
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Ubicación (Apéndice G.4): FusedLocationProviderClient.getCurrentLocation()
    // para adjuntar lat/lon/place_label al gasto. .await() vía
    // kotlinx-coroutines-play-services (ya presente más abajo).
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Jetpack Glance — widget de pantalla de inicio para captura rápida (§G.3).
    // Runtime de Compose propio de Glance, independiente del compose-bom.
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Gemini Nano on-device: ML Kit GenAI Prompt API (vía GA, sin allowlist por-app;
    // maneja descarga del modelo con checkStatus()/download()). Reemplaza al SDK
    // experimental `aicore` (que daba PERMISSION_DENIED en el canal de terceros).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")

    // LiteRT-LM: corre un modelo Gemma .litertlm local en CPU/GPU/NPU, independiente
    // de AICore. Es el camino que SÍ funciona en Tensor G4 (Pixel 9 / Fold de Norma),
    // donde AICore aún no provisiona el feature del Prompt API (FEATURE_NOT_FOUND).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")

    // ── Fase C (paquete C1): importar estados de cuenta bancarios con LLM cloud ──
    // Extracción de texto LOCAL del PDF (nada del PDF crudo viaja a la nube: solo
    // el texto). pdfbox-android es el port de Apache PDFBox para Android.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // OCR on-device de imágenes de estados de cuenta (JPG/PNG). Mismo stack ML Kit
    // que ya usa genai-prompt; el modelo latino viene empacado (bundled).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Cliente HTTP para el endpoint OpenAI-compatible de NVIDIA NIM. Firestore ya
    // trae OkHttp transitivo, pero se declara explícito para fijar la API.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Compose Material 3 & Essentials
    implementation("androidx.compose.material3:material3")
    // Material 3 Adaptive — NavigationSuiteScaffold vive aquí (graduado en material3 1.3)
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    // SupportingPaneScaffold + PaneExpansionState (divisor de paneles arrastrable)
    implementation("androidx.compose.material3.adaptive:adaptive:1.1.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.1.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.1.0")
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

    // Fase B (multi-tenant): Google Sign-In vía Credential Manager. Reemplaza al
    // legacy GoogleSignInClient. `googleid` aporta GetGoogleIdOption/GoogleIdTokenCredential.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
