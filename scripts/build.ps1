param(
    [ValidateSet('debug', 'release', 'nightly', 'redesign')]
    [string]$Variant = 'debug'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$task = switch ($Variant) {
    'debug' { 'assembleDebug' }
    'release' { 'assembleRelease' }
    'nightly' { 'assembleNightly' }
    'redesign' { 'assembleRedesign' }
}

Write-Host "Building Kosen ($Variant)..." -ForegroundColor Cyan
& .\gradlew.bat $task

$apkDir = Join-Path $Root "app\build\outputs\apk\$Variant"
$apk = Get-ChildItem -Path $apkDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($apk) {
    Write-Host "APK: $($apk.FullName)" -ForegroundColor Green
} else {
    Write-Host "Build finished, but APK not found in $apkDir" -ForegroundColor Yellow
}
