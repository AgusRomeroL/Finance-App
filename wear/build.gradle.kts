import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Mismo archivo de secretos que :app, leido desde la raiz: el emparejamiento
// companion exige que reloj y telefono vayan firmados con la MISMA llave, y
// compartir el origen es la unica forma de que no se separen por descuido.
// Sin secrets.local.properties no hay signingConfig y el release sale sin firmar.
val secretsFile = rootProject.file("secrets.local.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
val hasReleaseKeystore = secretsFile.exists() && secrets.getProperty("storeFile") != null

android {
    namespace = "mx.budget.wear"
    compileSdk = 36

    defaultConfig {
        // Mismo applicationId que el teléfono: requisito de la app companion Wear
        // (emparejamiento por package). El namespace (mx.budget.wear) sí difiere.
        applicationId = "mx.budget"
        minSdk = 30 // Wear OS 3+
        targetSdk = 36
        // Misma fuente unica que :app (gradle.properties). Igualdad por
        // construccion: el reloj no puede quedar por delante del telefono.
        versionCode = providers.gradleProperty("budget.versionCode").get().toInt()
        versionName = providers.gradleProperty("budget.versionName").get()
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(secrets.getProperty("storeFile"))
                storePassword = secrets.getProperty("storePassword")
                keyAlias = secrets.getProperty("keyAlias")
                keyPassword = secrets.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Igual que en :app, la reduccion de codigo es de la Fase 8. En el
            // reloj es aun mas delicada: ProtoLayout y los TileService se
            // resuelven por nombre desde el sistema.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Horologist marca su layout API (AppScaffold/ScreenScaffold/responsive column)
        // como experimental; optamos in a nivel de módulo en vez de anotar cada uso.
        freeCompilerArgs.add("-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi")
    }
}

dependencies {
    // Constantes compartidas del Data Layer telefono-reloj (MVP Fase 5).
    implementation(project(":wearcore"))

    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Wear Compose (Material + Foundation) + navegación del hub.
    // ≥1.5: captura la SecurityException de leer `reduce_motion` con
    // targetSdk 36 en Wear OS API ≤ 34: con 1.4.0 el hub crasheaba al abrir
    // en Pixel Watch 3 / Wear OS 4 (P0 de auditoría runtime 2026-07).
    implementation("androidx.wear.compose:compose-material:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-navigation:1.6.2")

    // Horologist: AppScaffold/ScreenScaffold + ScalingLazyColumn responsivo
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.17")

    // Wear OS Data Layer (mismo que el :app) + .await()
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Tiles (ProtoLayout), el camino oficial y estable, renderizado por el
    // sistema (sin recomposición de Compose). Reemplaza a glance-wear-tiles alpha,
    // que crasheaba ("Glance Wear Tile Error") y hundía el FPS del reloj.
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    // SuspendingTileService: onTileRequest en corrutina (para await del Data Layer).
    implementation("com.google.android.horologist:horologist-tiles:0.6.17")
}
