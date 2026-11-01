// R8 fijado por encima del que trae AGP 8.11.1 (Fase 8). Algunas bibliotecas ya
// traen metadata de Kotlin 2.3 y el R8 empaquetado no la entiende ("An error
// occurred when parsing kotlin metadata"); la tabla oficial de compatibilidad
// (developer.android.com/build/kotlin-support) pide R8 8.13.19 para Kotlin 2.3.
// Al subir AGP a una version que ya lo traiga, este bloque sobra.
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:8.13.19")
    }
}

plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.2" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    // Observabilidad (Fase 7, decision 7 del plan): Crashlytics.
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
