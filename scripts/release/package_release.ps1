<#
.SYNOPSIS
  Compila, verifica y deja en Drive el release firmado del telefono y del reloj
  (Fase 8, decision 5: distribucion por Drive).

.DESCRIPTION
  1. Exige la llave: sin storeFile en secrets.local.properties el release sale
     sin firmar y no sirve para distribuir.
  2. Lee la version de gradle.properties (fuente unica de :app y :wear) y se
     niega a pisar una carpeta de release que ya exista.
  3. Refleja el proyecto Gradle en una copia NTFS (-BuildDir) y compila ahi
     assembleRelease de :app y :wear (R8 y subida del mapping a Crashlytics).
     Sobre el volumen de Google Drive el mismo build tarda unas cinco veces mas
     (26 min contra 5 el 2026-11-01): la fusion de recursos copia miles de
     archivos y cada copia pasa por Drive.
  4. Verifica: un solo firmante, el MISMO certificado en los dos APK (lo exige
     el emparejamiento companion), zipalign -P 16 y la alineacion ELF de 16 KB.
  5. Copia los APK, el mapping de R8, SHA256SUMS.txt e INSTALAR.md a
     <Out>\<versionName>-<versionCode>\.

  Compartir la carpeta es un paso aparte y manual.

.PARAMETER Out
  Carpeta base de los releases, fuera del repo. Por defecto
  G:\My Drive\Apps\Finance-App-Releases.

.PARAMETER BuildDir
  Copia NTFS donde se compila. Por defecto C:\dev\finance-build. Se refleja
  con robocopy /MIR en cada corrida (solo el proyecto Gradle: nada de web,
  Excel, estados de cuenta ni service-account.json); secrets.local.properties
  y local.properties se copian porque la firma y el SDK los necesitan.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\release\package_release.ps1
#>
param(
    [string]$Out = 'G:\My Drive\Apps\Finance-App-Releases',
    [string]$BuildDir = 'C:\dev\finance-build'
)

$ErrorActionPreference = 'Stop'

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$BuildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName 'apksigner.bat') } |
    Sort-Object { [version]($_.Name -replace '-.*$', '') } -Descending |
    Select-Object -First 1
if (-not $BuildTools) { throw 'No encontre build-tools con apksigner en el SDK.' }
$Apksigner = Join-Path $BuildTools.FullName 'apksigner.bat'
$Zipalign = Join-Path $BuildTools.FullName 'zipalign.exe'

function Read-Props([string]$Path) {
    $props = @{}
    foreach ($line in Get-Content $Path) {
        if ($line -match '^\s*([^#=\s][^=]*?)\s*=\s*(.*)$') { $props[$Matches[1]] = $Matches[2] }
    }
    return $props
}

# 1. Llave.
$secretsPath = Join-Path $Repo 'secrets.local.properties'
if (-not (Test-Path $secretsPath) -or -not (Read-Props $secretsPath).ContainsKey('storeFile')) {
    throw 'Falta storeFile en secrets.local.properties: sin llave el release sale sin firmar. Ver CLAUDE.md, "Como se publica".'
}

# 2. Version y destino.
$gradleProps = Read-Props (Join-Path $Repo 'gradle.properties')
$versionCode = $gradleProps['budget.versionCode']
$versionName = $gradleProps['budget.versionName']
$tag = "$versionName-$versionCode"
$dest = Join-Path $Out $tag
if (Test-Path $dest) { throw "Ya existe $dest. Sube budget.versionCode en gradle.properties para un release nuevo." }
$dirty = & git -C $Repo status --porcelain
if ($dirty) { Write-Warning 'El arbol de trabajo tiene cambios sin commit: el release no corresponde a un commit exacto.' }
$commit = (& git -C $Repo rev-parse --short HEAD).Trim()
Write-Host "==> Release $tag (commit $commit)"

# 3. Espejo NTFS y build. gradlew.bat necesita un java para arrancar (ver CLAUDE.md).
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
foreach ($f in 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew.bat', 'local.properties', 'secrets.local.properties') {
    Copy-Item (Join-Path $Repo $f) (Join-Path $BuildDir $f) -Force
}
foreach ($d in 'gradle', 'wrapper', 'app', 'wear', 'wearcore') {
    robocopy (Join-Path $Repo $d) (Join-Path $BuildDir $d) /MIR /XD build .gradle .cxx /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
    # robocopy: 0 a 7 es exito (1 = copio archivos); 8 o mas es error.
    if ($LASTEXITCODE -ge 8) { throw "robocopy de $d fallo con codigo $LASTEXITCODE." }
}
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Push-Location $BuildDir
try {
    & .\gradlew.bat --console=plain :app:assembleRelease :wear:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease fallo con codigo $LASTEXITCODE." }
}
finally { Pop-Location }

$appApk = Join-Path $BuildDir 'app\build\outputs\apk\release\app-release.apk'
$wearApk = Join-Path $BuildDir 'wear\build\outputs\apk\release\wear-release.apk'
foreach ($apk in @($appApk, $wearApk)) {
    if (-not (Test-Path $apk)) { throw "No se genero $apk (sin firma?)." }
}

# 4. Verificaciones.
function Get-CertSha256([string]$Apk) {
    $out = & $Apksigner verify --print-certs $Apk
    if ($LASTEXITCODE -ne 0) { throw "apksigner rechazo $Apk." }
    $signers = @($out | Select-String -Pattern 'Signer #(\d+) certificate DN' ).Count
    if ($signers -ne 1) { throw "$Apk tiene $signers firmantes; se esperaba uno." }
    $line = $out | Select-String -Pattern 'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)'
    return $line.Matches[0].Groups[1].Value
}
$appCert = Get-CertSha256 $appApk
$wearCert = Get-CertSha256 $wearApk
if ($appCert -ne $wearCert) { throw 'El telefono y el reloj estan firmados con certificados distintos: el emparejamiento no funcionaria.' }
Write-Host "==> Firma OK, mismo certificado en los dos (SHA-256 $appCert)"

foreach ($apk in @($appApk, $wearApk)) {
    & $Zipalign -c -P 16 -v 4 $apk | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "zipalign -P 16 rechazo $apk." }
}
& python (Join-Path $Repo 'scripts\ci\check_16kb.py') $appApk $wearApk
if ($LASTEXITCODE -ne 0) { throw 'Hay bibliotecas nativas sin alinear a 16 KB.' }

# 5. Copia a Drive.
New-Item -ItemType Directory -Force -Path $dest | Out-Null
$appOut = Join-Path $dest "presupuesto-$tag.apk"
$wearOut = Join-Path $dest "presupuesto-reloj-$tag.apk"
Copy-Item $appApk $appOut
Copy-Item $wearApk $wearOut
Copy-Item (Join-Path $BuildDir 'app\build\outputs\mapping\release\mapping.txt') (Join-Path $dest "mapping-app-$tag.txt")
Copy-Item (Join-Path $BuildDir 'wear\build\outputs\mapping\release\mapping.txt') (Join-Path $dest "mapping-reloj-$tag.txt")
Copy-Item (Join-Path $PSScriptRoot 'INSTALAR.md') (Join-Path $dest 'INSTALAR.md')
$sums = foreach ($f in @($appOut, $wearOut)) {
    '{0}  {1}' -f (Get-FileHash -Algorithm SHA256 $f).Hash.ToLower(), (Split-Path $f -Leaf)
}
$sums | Set-Content -Path (Join-Path $dest 'SHA256SUMS.txt') -Encoding ascii
"commit $commit`nversionCode $versionCode`nversionName $versionName" |
    Set-Content -Path (Join-Path $dest 'VERSION.txt') -Encoding ascii

Write-Host "==> Listo: $dest"
Get-ChildItem $dest | Format-Table Name, Length -AutoSize
