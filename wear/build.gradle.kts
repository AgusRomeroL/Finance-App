plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "mx.budget.wear"
    compileSdk = 34

    defaultConfig {
        // Mismo applicationId que el teléfono: requisito de la app companion Wear
        // (emparejamiento por package). El namespace (mx.budget.wear) sí difiere.
        applicationId = "mx.budget"
        minSdk = 30 // Wear OS 3+
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
        // Horologist marca su layout API (AppScaffold/ScreenScaffold/responsive column)
        // como experimental; optamos in a nivel de módulo en vez de anotar cada uso.
        freeCompilerArgs += "-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Wear Compose (Material + Foundation)
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    // Horologist — AppScaffold/ScreenScaffold + ScalingLazyColumn responsivo
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.17")

    // Wear OS Data Layer (mismo que el :app) + .await()
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Glance Tile para el vistazo de saldo en la home del reloj
    implementation("androidx.glance:glance-wear-tiles:1.0.0-alpha05")
}
