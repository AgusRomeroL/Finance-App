// Módulo compartido teléfono↔reloj (MVP Fase 5): SOLO constantes (WearPaths).
// Kotlin JVM puro (sin Android) para que compile en segundos y ambos APKs
// consuman UNA sola fuente de verdad de los paths/keys del Data Layer — antes
// eran dos copias mantenidas a mano que podían divergir silenciosamente.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}
