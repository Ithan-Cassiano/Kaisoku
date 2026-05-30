param(
	[Parameter(Mandatory = $true)]
	[string]$Version,
	[string]$ApkPath = "",
	[string]$Token = $env:GH_TOKEN,
	[string]$Repo = "Ithan-Cassiano/Kaisoku"
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

$tag = if ($Version.StartsWith('v')) { $Version } else { "v$Version" }
$ver = $tag.TrimStart('v')

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
	$candidates = @(
		(Join-Path $PSScriptRoot "..\..\Kosen-$tag.apk"),
		(Join-Path $PSScriptRoot "..\app\build\outputs\apk\release\app-release.apk"),
		(Join-Path $PSScriptRoot "..\..\Kosen-v$ver.apk")
	)
	foreach ($c in $candidates) {
		if (Test-Path $c) { $ApkPath = (Resolve-Path $c).Path; break }
	}
}

if (-not (Test-Path $ApkPath)) {
	Write-Error "APK não encontrado. Informe -ApkPath."
}

$apkName = "Kosen-$tag.apk"
$headers = @{
	Authorization = "Bearer $Token"
	Accept = "application/vnd.github+json"
	"X-GitHub-Api-Version" = "2022-11-28"
}

Write-Host "Criando release $tag em $Repo..." -ForegroundColor Cyan
$releaseBody = @{
	tag_name = $tag
	name = "Kosen $ver"
	body = "Release Kosen $ver"
	draft = $false
	prerelease = $false
} | ConvertTo-Json

try {
	$release = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/$Repo/releases" -Headers $headers -Body $releaseBody -ContentType "application/json; charset=utf-8"
} catch {
	if ($_.Exception.Response.StatusCode.value__ -eq 422) {
		Write-Host "Release já existe, buscando..." -ForegroundColor Yellow
		$release = Invoke-RestMethod -Method Get -Uri "https://api.github.com/repos/$Repo/releases/tags/$tag" -Headers $headers
	} else {
		throw
	}
}

$uploadUrl = $release.upload_url -replace '\{.*$', "?name=$apkName"
Write-Host "Enviando APK ($([math]::Round((Get-Item $ApkPath).Length/1MB, 2)) MB)..." -ForegroundColor Cyan

$uploadHeaders = @{
	Authorization = "Bearer $Token"
	Accept = "application/vnd.github+json"
	"Content-Type" = "application/vnd.android.package-archive"
}

Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $uploadHeaders -InFile $ApkPath | Out-Null

Write-Host "Release publicado: $($release.html_url)" -ForegroundColor Green
