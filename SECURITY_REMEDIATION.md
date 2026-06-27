# Remediación de Seguridad — Finance-App

> Fecha de detección: 2026-06-27
> Proyecto Firebase afectado: `finance-app-abdf9`
> Remoto git: `https://github.com/AgusRomeroL/Finance-App.git` (origin)

Este documento describe las acciones de remediación. **Las acciones ya ejecutadas** son seguras y reversibles (cambios staged en git, sin commit). **Las acciones pendientes** requieren intervención manual tuya y, en algunos casos, son irreversibles (rotación de claves, reescritura de historial). Léelo completo antes de actuar.

---

## 1. CRÍTICO Y URGENTE — Rotar/revocar la private key comprometida

El archivo `service-account.json` (raíz del repo) fue **commiteado al historial de git** y contiene la **private key completa** de una cuenta de servicio de Firebase Admin SDK del proyecto `finance-app-abdf9`. Como existe un remoto en GitHub, **debes asumir que la clave ya está expuesta públicamente** (incluso si el repo es privado: forks, clones, caches y logs de CI pueden retenerla).

Quitarla del tracking **NO** invalida la clave. La clave sigue siendo válida hasta que la revoques en GCP. **Esto solo lo puedes hacer tú.**

### Pasos en la consola de GCP (IAM)

1. Entra a la [Google Cloud Console](https://console.cloud.google.com/) con el proyecto `finance-app-abdf9` seleccionado.
2. Ve a **IAM & Admin > Service Accounts**.
3. Busca la cuenta `firebase-adminsdk-...@finance-app-abdf9.iam.gserviceaccount.com` (el `client_email` que aparece dentro de `service-account.json`).
4. Abre la cuenta y ve a la pestaña **Keys**.
5. Identifica la key cuyo `private_key_id` coincide con el del JSON comprometido. **Bórrala (Delete)** — esto la revoca inmediatamente.
6. Pulsa **Add Key > Create new key > JSON** para generar una clave nueva.
7. Guarda el nuevo JSON **fuera del repo** (o en la raíz, ya cubierto por `.gitignore`) y actualiza tu configuración local/servidor.
8. Revisa los **logs de auditoría** del proyecto (Logging > Logs Explorer) por uso sospechoso de la clave vieja entre la fecha del primer commit y hoy.

> Hasta completar el paso 5, la clave comprometida sigue activa.

---

## 2. Purgar `service-account.json` (y la basura) del historial de git

Quitar el archivo del tracking (ya hecho, ver sección 4) **no lo borra del historial**: cualquiera con acceso al repo puede recuperarlo de commits anteriores. Para eliminarlo de TODA la historia hay que **reescribir el historial**.

> **ADVERTENCIA:** Reescribir el historial cambia todos los SHA de los commits afectados. Como **hay un remoto (`origin` en GitHub)**, tras la reescritura necesitarás un **`git push --force`** y todos los colaboradores deberán re-clonar o resetear sus copias. Coordina antes de hacerlo. Haz un backup del repo (`cp -r` o un clon espejo) antes de empezar.

### Opción A — `git filter-repo` (recomendada)

Instalación: `pip install git-filter-repo` (o `brew install git-filter-repo`).

```bash
# Desde la raíz del repo, con el árbol de trabajo limpio:
git filter-repo \
  --invert-paths \
  --path service-account.json \
  --path budget_database.db \
  --path etl_output.log \
  --path-glob 'java_pid*.hprof'
```

`filter-repo` elimina por seguridad el remoto `origin`. Vuelve a añadirlo y fuerza el push:

```bash
git remote add origin https://github.com/AgusRomeroL/Finance-App.git
git push origin --force --all
git push origin --force --tags
```

### Opción B — BFG Repo-Cleaner (alternativa)

```bash
# Requiere Java. Descarga bfg.jar de https://rtyley.github.io/bfg-repo-cleaner/
git clone --mirror https://github.com/AgusRomeroL/Finance-App.git
java -jar bfg.jar --delete-files service-account.json Finance-App.git
java -jar bfg.jar --delete-files '{*.hprof,etl_output.log}' Finance-App.git
java -jar bfg.jar --delete-files budget_database.db Finance-App.git
cd Finance-App.git
git reflog expire --expire=now --all && git gc --prune=now --aggressive
git push --force
```

> Nota: BFG no purga archivos en el commit HEAD actual; por eso primero se quitaron del tracking (sección 4). `filter-repo` sí los purga en todos los commits.

### Después de purgar (con cualquier opción)

- Pide a GitHub que **invalide caches** / borre forks si el repo fue público.
- Aunque purgues el historial, **la clave ya estuvo expuesta**: la rotación de la sección 1 sigue siendo obligatoria e independiente de esto.

---

## 3. Restringir la API key de `google-services.json`

`app/google-services.json` contiene la API key de Firebase del cliente Android. No es tan crítica como la service account, pero **debe restringirse** para evitar abuso de cuota y acceso no autorizado.

1. En GCP Console > **APIs & Services > Credentials**, localiza la **API key** que aparece en `app/google-services.json` (`current_key` / `api_key`).
2. Edítala y en **Application restrictions** elige **Android apps**.
3. Añade el package name `mx.budget` junto con el **SHA-1** de tu firma:
   - Debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
   - Release: el SHA-1 de tu keystore de producción.
4. En **API restrictions**, limita la key únicamente a las APIs de Firebase que la app usa.

> `google-services.json` puede permanecer en el repo (es estándar en Android), pero la seguridad real proviene de las restricciones de la API key + reglas de seguridad de Firebase, no de ocultar el archivo.

---

## 4. Acciones ya ejecutadas (seguras y reversibles)

Hechas automáticamente, **sin commit** (cambios staged). Ningún archivo fue borrado del disco.

- **`.gitignore` actualizado** con: `service-account.json`, `*.hprof`, `/budget_database.db` (solo la de la raíz; la de `app/src/main/assets/` se conserva trackeada), `etl_output.log`, y patrones de credenciales comunes (`*.pem`, `*.p12`, `*.keystore`, `*.jks`, `serviceAccountKey.json`).
- **Removidos del tracking** (`git rm --cached`, conservados en disco):
  - `service-account.json`
  - `budget_database.db` (raíz)
  - `etl_output.log`
  - `java_pid37576.hprof`, `java_pid44884.hprof`, `java_pid55028.hprof`, `java_pid55412.hprof`, `java_pid55692.hprof`

### Para confirmar estos cambios

```bash
git status            # revisa los archivos staged como "deleted"
git commit -m "chore(security): dejar de trackear secretos y artefactos; reforzar .gitignore"
```

> Este commit **no** purga el historial; solo deja de trackear los archivos de aquí en adelante. La purga real es la sección 2.

---

## Checklist de cierre

- [ ] **(URGENTE)** Revocar la private key vieja en GCP IAM y crear una nueva (sección 1).
- [ ] Revisar logs de auditoría por uso indebido de la clave.
- [ ] Commitear los cambios staged (sección 4).
- [ ] Backup del repo y purga del historial con `filter-repo`/BFG + `push --force` (sección 2).
- [ ] Restringir la API key de `google-services.json` por SHA-1 + `mx.budget` (sección 3).
- [ ] Borrar este archivo o moverlo a un lugar privado una vez completado.
