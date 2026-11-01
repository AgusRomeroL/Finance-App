# Reglas de R8 del reloj (Fase 8). Los TileService, los servicios de
# complications y las Activity estan en el manifest y se conservan solos;
# ProtoLayout, Horologist y el Data Layer (org.json dentro de un DataMap, bytes
# separados por "|" hacia el telefono) no usan reflexion.

# Trazas con archivo y linea en logcat, legibles con el mapping del build.
-keepattributes SourceFile,LineNumberTable
