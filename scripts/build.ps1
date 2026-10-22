param([switch]$ConnectedTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'Définissez JAVA_HOME vers un JDK 17 ou 21 avant de compiler.'
}
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Échec de la compilation ou des vérifications.' }
    if ($ConnectedTests) {
        & (Join-Path $projectRoot 'gradlew.bat') :app:connectedDebugAndroidTest --console plain
        if ($LASTEXITCODE -ne 0) { throw 'Échec des tests sur appareil.' }
    }
    Write-Host 'APK : app/build/outputs/apk/debug/app-debug.apk'
} finally {
    Pop-Location
}
