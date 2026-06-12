param(
	[Parameter(Mandatory = $true)]
	[string]$Version,
	[Parameter(Mandatory = $false)]
	[string]$Description = "",
	[Parameter(Mandatory = $false)]
	[string]$DescriptionFile = "",
	[string]$ApkPath = "",
	[string]$ApkName = "",
	[ValidateSet('release', 'dev')]
	[string]$Channel = 'release',
	[string]$Token = $env:GH_TOKEN,
	[string]$Repo = ""
)

$ErrorActionPreference = 'Stop'
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false

if ([string]::IsNullOrWhiteSpace($Repo)) {
	$Repo = if ($Channel -eq 'dev') {
		"Ithan-Cassiano/Kosen-Dev-Releases"
	} else {
		"Ithan-Cassiano/Kosen-Releases"
	}
}

$tag = if ($Version.StartsWith('v')) { $Version } else { "v$Version" }
$ver = $tag.TrimStart('v')
$isDevChannel = $Channel -eq 'dev'

if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
	if (-not (Test-Path $DescriptionFile)) {
		$notesSubdir = if ($isDevChannel) { 'dev' } else { 'release' }
		$DescriptionFile = Join-Path $PSScriptRoot "..\release-notes\$notesSubdir\v$ver.md"
	}
	$Description = [System.IO.File]::ReadAllText((Resolve-Path $DescriptionFile).Path, $Utf8NoBom)
} elseif ([string]::IsNullOrWhiteSpace($Description)) {
	$notesSubdir = if ($isDevChannel) { 'dev' } else { 'release' }
	$autoNotes = Join-Path $PSScriptRoot "..\release-notes\$notesSubdir\v$ver.md"
	if (Test-Path $autoNotes) {
		$Description = [System.IO.File]::ReadAllText((Resolve-Path $autoNotes).Path, $Utf8NoBom)
	}
}

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

$Description = $Description.Trim()

function Get-AppVersionCode {
	param([ValidateSet('release', 'dev')][string]$ChannelName = 'release')
	$gradle = Join-Path $PSScriptRoot "..\app\build.gradle"
	if (-not (Test-Path $gradle)) { return 0 }
	$pattern = if ($ChannelName -eq 'dev') {
		'devVersionCode\s*=\s*(\d+)'
	} else {
		'releaseVersionCode\s*=\s*(\d+)'
	}
	$m = Select-String -Path $gradle -Pattern $pattern | Select-Object -First 1
	if ($m) { return [int]$m.Matches.Groups[1].Value }
	return 0
}

function Add-VersionCodeMarker {
	param([string]$Text)
	$code = Get-AppVersionCode -ChannelName $Channel
	if ($code -le 0 -or $Text -match 'versionCode:\d+') {
		return $Text
	}
	return ($Text.TrimEnd() + "`n`n[versionCode:$code]")
}

function Add-ChannelMarker {
	param([string]$Text)
	$marker = if ($isDevChannel) { '[channel:dev]' } else { '[channel:release]' }
	if ($Text -match '\[channel:(dev|release)\]') {
		return $Text
	}
	return ($Text.TrimEnd() + "`n`n$marker")
}

$Description = Add-VersionCodeMarker $Description
$Description = Add-ChannelMarker $Description

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
	$candidates = if ($isDevChannel) {
		@(
			(Join-Path $PSScriptRoot "..\..\Kosen-Dev-$ver-debug.apk"),
			(Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk")
		)
	} else {
		@(
			(Join-Path $PSScriptRoot "..\..\Kosen-$tag.apk"),
			(Join-Path $PSScriptRoot "..\app\build\outputs\apk\release\app-release.apk"),
			(Join-Path $PSScriptRoot "..\..\Kosen-v$ver.apk")
		)
	}
	foreach ($c in $candidates) {
		if (Test-Path $c) { $ApkPath = (Resolve-Path $c).Path; break }
	}
}

if (-not (Test-Path $ApkPath)) {
	Write-Error "APK não encontrado. Informe -ApkPath."
}

if ([string]::IsNullOrWhiteSpace($ApkName)) {
	$ApkName = if ($isDevChannel) { "Kosen-Dev-$ver-debug.apk" } else { "Kosen-$tag.apk" }
}
$apkName = $ApkName
$releaseName = if ($isDevChannel) { "Kosen Dev $ver" } else { "Kosen $ver" }
$headers = @{
	Authorization = "Bearer $Token"
	Accept = "application/vnd.github+json"
	"X-GitHub-Api-Version" = "2022-11-28"
}

function Update-ReleaseMetadata {
	param($ReleaseId)
	if (-not [string]::IsNullOrWhiteSpace($DescriptionFile) -and -not $isDevChannel) {
		$notesScript = Join-Path $PSScriptRoot "update-release-notes.py"
		$verArg = $tag.TrimStart('v')
		python $notesScript $Token $Repo $verArg
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

Write-Host "Criando release $tag em $Repo (canal: $Channel)..." -ForegroundColor Cyan
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

if (-not [string]::IsNullOrWhiteSpace($DescriptionFile) -and -not $isDevChannel) {
	$notesScript = Join-Path $PSScriptRoot "update-release-notes.py"
	$verArg = $tag.TrimStart('v')
	python $notesScript $Token $Repo $verArg
}

Write-Host "Release publicado: $($release.html_url)" -ForegroundColor Green
