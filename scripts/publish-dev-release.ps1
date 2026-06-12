param(
	[Parameter(Mandatory = $true)]
	[string]$Version,
	[Parameter(Mandatory = $false)]
	[string]$Description = "",
	[Parameter(Mandatory = $false)]
	[string]$DescriptionFile = "",
	[string]$ApkPath = "",
	[string]$Token = ""
)

$ErrorActionPreference = 'Stop'

# Publica no repo PRIVADO Ithan-Cassiano/Kosen-Dev-Releases (não tornar public).

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
	$ApkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
}

if (-not (Test-Path $ApkPath)) {
	throw "APK de debug não encontrado em '$ApkPath'. Execute .\build.ps1 -Variant debug primeiro."
}

$exportedApk = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) "Kosen-Dev-$Version-debug.apk"
if (Test-Path $exportedApk) { Remove-Item $exportedApk -Force }
Copy-Item $ApkPath $exportedApk -Force
Write-Host "Cópia local: $exportedApk" -ForegroundColor Green

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

$params = @{
	Version = $Version
	ApkPath = $ApkPath
	ApkName = "Kosen-Dev-$Version-debug.apk"
	Channel = 'dev'
	Token = $Token
	Repo = "Ithan-Cassiano/Kosen-Dev-Releases"
}
if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
	$params.DescriptionFile = $DescriptionFile
} elseif (Test-Path (Join-Path $PSScriptRoot "..\release-notes\dev\v$Version.md")) {
	$params.DescriptionFile = Join-Path $PSScriptRoot "..\release-notes\dev\v$Version.md"
} else {
	$params.Description = $Description
}

& (Join-Path $PSScriptRoot "publish-release.ps1") @params
