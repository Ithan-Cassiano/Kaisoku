param(
	[Parameter(Mandatory = $true)]
	[string]$Version,
	[Parameter(Mandatory = $false)]
	[string]$Description = "",
	[Parameter(Mandatory = $false)]
	[string]$DescriptionFile = "",
	[string]$ApkPath = "",
	[string]$Token = $env:GH_TOKEN,
	[string]$Repo = "Ithan-Cassiano/Kaisoku"
)

$ErrorActionPreference = 'Stop'
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false

if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
	$Description = [System.IO.File]::ReadAllText((Resolve-Path $DescriptionFile).Path, $Utf8NoBom)
}

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

$tag = if ($Version.StartsWith('v')) { $Version } else { "v$Version" }
$ver = $tag.TrimStart('v')
$Description = $Description.Trim()

if ([string]::IsNullOrWhiteSpace($Description)) {
	Write-Error "Descrição da release obrigatória. Use -Description '...' ou -DescriptionFile."
}

function Invoke-GitHubJson {
	param(
		[string]$Method,
		[string]$Uri,
		[hashtable]$Headers,
		[hashtable]$Body
	)
	$json = $Body | ConvertTo-Json -Depth 5
	$bytes = $Utf8NoBom.GetBytes($json)
	Invoke-RestMethod -Method $Method -Uri $Uri -Headers $Headers -Body $bytes -ContentType "application/json; charset=utf-8"
}

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
$releaseName = "Kosen $ver"
$headers = @{
	Authorization = "Bearer $Token"
	Accept = "application/vnd.github+json"
	"X-GitHub-Api-Version" = "2022-11-28"
}

function Update-ReleaseMetadata {
	param($ReleaseId)
	if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
		$notesScript = Join-Path $PSScriptRoot "update-release-notes.py"
		$ver = $tag.TrimStart('v')
		python $notesScript $Token $ver
		return
	}
	$patchBody = @{
		name = $releaseName
		body = $Description
	}
	Invoke-GitHubJson -Method Patch `
		-Uri "https://api.github.com/repos/$Repo/releases/$ReleaseId" `
		-Headers $headers `
		-Body $patchBody
}

Write-Host "Criando release $tag em $Repo..." -ForegroundColor Cyan
$releaseBody = @{
	tag_name = $tag
	name = $releaseName
	body = $Description
	draft = $false
	prerelease = $false
}

try {
	$release = Invoke-GitHubJson -Method Post -Uri "https://api.github.com/repos/$Repo/releases" -Headers $headers -Body $releaseBody
} catch {
	if ($_.Exception.Response.StatusCode.value__ -eq 422) {
		Write-Host "Release já existe, atualizando descrição..." -ForegroundColor Yellow
		$release = Invoke-RestMethod -Method Get -Uri "https://api.github.com/repos/$Repo/releases/tags/$tag" -Headers $headers
		Update-ReleaseMetadata -ReleaseId $release.id
	} else {
		throw
	}
}

$existingAsset = $release.assets | Where-Object { $_.name -eq $apkName }
if ($existingAsset) {
	Write-Host "Removendo APK anterior..." -ForegroundColor Yellow
	Invoke-RestMethod -Method Delete -Uri "https://api.github.com/repos/$Repo/releases/assets/$($existingAsset.id)" -Headers $headers | Out-Null
}

$uploadUrl = $release.upload_url -replace '\{.*$', "?name=$apkName"
Write-Host "Enviando APK ($([math]::Round((Get-Item $ApkPath).Length/1MB, 2)) MB)..." -ForegroundColor Cyan

$uploadHeaders = @{
	Authorization = "Bearer $Token"
	Accept = "application/vnd.github+json"
	"Content-Type" = "application/vnd.android.package-archive"
}

Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $uploadHeaders -InFile $ApkPath | Out-Null

if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
	$notesScript = Join-Path $PSScriptRoot "update-release-notes.py"
	$ver = $tag.TrimStart('v')
	python $notesScript $Token $ver
}

Write-Host "Release publicado: $($release.html_url)" -ForegroundColor Green
