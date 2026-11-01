# Reglas de R8 del telefono (Fase 8). Room, WorkManager, kotlinx.serialization,
# OkHttp, ML Kit, pdfbox-android, Firebase y los componentes del manifest traen
# sus propias reglas o se conservan solos; aqui va SOLO lo que la auditoria de
# reflexion del 2026-10-31 encontro sin cubrir. Cada regla dice por que existe:
# si su motivo desaparece, la regla se va con el.

# Trazas legibles en Crashlytics: el mapping traduce nombres, pero sin archivo y
# linea la traza no dice donde fallo. Signature y las anotaciones las necesita
# el CustomClassMapper de Firestore para leer tipos genericos y @PropertyName.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Firestore serializa por reflexion diez entidades Room con set(entity) (los
# *RepositoryFirestore de data/remote): el nombre de cada getter ES la clave del
# documento. Renombrados, los documentos subirian con claves "a", "b" y el pull,
# que lee por nombre (FirestoreMappers), perderia los datos en silencio.
-keepclassmembers class mx.budget.data.local.entity.** { *; }

# LiteRT-LM no publica reglas y su codigo nativo llama de vuelta a clases Java
# por nombre (LiteRtLmJni$Jni*Callback, Session/Conversation$Jni*Impl). Un
# renombre aqui no falla al compilar: revienta al cargar el modelo.
-keep class com.google.ai.edge.litertlm.** { *; }

# pdfbox-android carga el decodificador JPEG 2000 por nombre solo si existe; la
# app no lo empaca y los estados de cuenta no usan JPX.
-dontwarn com.gemalto.jp2.**

# Credential Manager encuentra su proveedor de Play services por reflexion. La
# biblioteca ya lo cubre; es la regla que pide su documentacion como seguro.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
