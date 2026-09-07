# Pruebas de las reglas de Firestore

Comprueban `firestore.rules` contra el emulador de Firestore, que evalúa el mismo
archivo que se despliega. No hacen falta credenciales ni cuentas reales: el
emulador acepta identidades simuladas, así que la prueba responde la pregunta que
importa ("¿un Colaborador puede leer el ledger?") sin iniciar sesión con nadie.

La API de pruebas de la Rules API no sirve aquí: el service account del proyecto
no tiene el permiso `firebaserules.rulesets.test`.

## Correr

Necesita Node y un JDK en el PATH. En este equipo el JDK es el de Android Studio,
que **no** está en el PATH por defecto, así que hay que ponerlo:

```bash
cd scripts/rules
npm install
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" PATH="$JAVA_HOME/bin:$PATH" npm test
```

En PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
npm test
```

El emulador lee `firestore.rules` desde `scripts/rules/`, así que antes de correr
hay que copiar el de la raíz:

```bash
cp ../../firestore.rules .
```

## Qué cubre (Fase 2 del cierre)

60 casos: qué lee y escribe cada rol (Dueño, Administrador, Colaborador), que un
Colaborador NO lee ninguna colección del ledger ni las atribuciones, que sí lee
lo que necesita para proponer, que quien no pertenece al hogar no lee nada, y que
una colección no declarada nace cerrada (lo que el comodín retirado abría).

Al añadir una colección al sync hay que añadir su regla y su caso aquí.

## Despliegue

`python scripts/admin/deploy_rules.py --service-account service-account.json`.
El deploy de hosting NO despliega reglas y la CLI exige un permiso de
`serviceusage` que el service account no tiene, por eso el script habla directo
con la Firebase Rules API. `--dry-run` solo imprime el ruleset activo.
