<#
.SYNOPSIS
  Compila, prueba y despliega la web (PWA) en Firebase Hosting de forma
  reproducible (Fase 8).

.DESCRIPTION
  npm no funciona sobre el volumen de Google Drive (G:): falla o se cuelga con
  TAR_ENTRY_ERROR/EPERM/EBADF. Por eso el build vive en una copia NTFS que este
  script sincroniza desde web/ antes de cada despliegue:

    1. robocopy /MIR de web/ a la copia (sin node_modules, dist, .firebase ni .env);
    2. npm ci, solo si cambio package-lock.json desde la ultima instalacion;
    3. npm test y npm run build;
    4. dist/version.json con el commit y la hora, para comprobar que se sirve;
    5. comprueba que hay sesion de Firebase y despliega solo hosting.

  La config de Firebase va embebida en web/src/lib/firebase.ts (es publica),
  asi que la copia no necesita .env. El script nunca hace login: si no hay
  sesion, se detiene y dice como iniciarla. Las reglas NO se despliegan aqui
  (scripts/admin/deploy_rules.py).

.PARAMETER Target
  Copia NTFS donde se compila. Por defecto C:\dev\finance-web.

.PARAMETER SkipTests
  Omite npm test (solo para reintentar un despliegue ya probado).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\web\deploy.ps1
#>
param(
    [string]$Target = 'C:\dev\finance-web',
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

# Version fija de la CLI: un despliegue reproducible no depende de la que este
# instalada (en esta maquina no hay ninguna). Subirla es un cambio deliberado.
$FirebaseTools = 'firebase-tools@15.34.0'
$Project = 'finance-app-abdf9'

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Source = Join-Path $Repo 'web'

function Invoke-Checked {
    param([string]$Label, [scriptblock]$Command)
    Write-Host "==> $Label"
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$Label fallo con codigo $LASTEXITCODE." }
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw 'No hay npm en el PATH. Instala Node 18 o superior.'
}

# 1. Sincronizar la copia. /MIR borra en la copia lo que ya no existe en web/,
# asi que un archivo eliminado del repo no sobrevive en el sitio publicado.
Write-Host "==> Sincronizando $Source -> $Target"
New-Item -ItemType Directory -Force -Path $Target | Out-Null
& robocopy $Source $Target /MIR /NFL /NDL /NJH /NJS /NP `
    /XD node_modules dist .firebase `
    /XF .env .env.local '*.tsbuildinfo' firebase-debug.log | Out-Null
# robocopy usa 0 a 7 para exito (con o sin copias) y 8 o mas para fallo.
if ($LASTEXITCODE -ge 8) { throw "robocopy fallo con codigo $LASTEXITCODE." }

Push-Location $Target
try {
    # 2. Dependencias, solo si cambio el lockfile.
    $lockHash = (Get-FileHash -Algorithm SHA256 'package-lock.json').Hash
    $stamp = Join-Path $Target 'node_modules\.deploy-lock-hash'
    $installed = $null
    if (Test-Path $stamp) { $installed = (Get-Content $stamp -Raw).Trim() }
    if ($installed -ne $lockHash) {
        Invoke-Checked 'npm ci' { npm ci --no-audit --no-fund }
        Set-Content -Path $stamp -Value $lockHash -Encoding ascii
    } else {
        Write-Host '==> npm ci omitido: package-lock.json sin cambios'
    }

    # 3. Pruebas y build (tsc -b y vite build).
    if (-not $SkipTests) { Invoke-Checked 'npm test' { npm test } }
    Invoke-Checked 'npm run build' { npm run build }

    # 4. Huella del despliegue: /version.json dice que commit se sirve.
    $commit = (& git -C $Repo rev-parse --short HEAD).Trim()
    $dirty = (& git -C $Repo status --porcelain -- web)
    if ($dirty) { $commit = "$commit-dirty" }
    $version = [ordered]@{ commit = $commit; builtAt = (Get-Date).ToUniversalTime().ToString('o') }
    $version | ConvertTo-Json | Set-Content -Path (Join-Path $Target 'dist\version.json') -Encoding ascii
    Write-Host "==> version.json: $commit"

    # 5. Sesion y despliegue. login:list sale con 0 aunque no haya cuenta, asi
    # que se lee su salida. Capturar stderr con Stop activo lo volveria error.
    $ErrorActionPreference = 'Continue'
    $accounts = (& npx --yes $FirebaseTools login:list 2>&1 | Out-String)
    $ErrorActionPreference = 'Stop'
    if ($accounts -notmatch 'Logged in as') {
        throw "No hay sesion de Firebase. Inicia una (una sola vez) con:`n  npx --yes $FirebaseTools login`ny vuelve a correr este script."
    }
    Invoke-Checked "firebase deploy ($Project)" {
        npx --yes $FirebaseTools deploy --only hosting --project $Project --non-interactive --message "web $commit"
    }
    Write-Host "==> Listo: https://$Project.web.app (version.json: $commit)"
}
finally {
    Pop-Location
}
