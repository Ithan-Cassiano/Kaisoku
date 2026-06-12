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

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

$params = @{
	Version = $Version
	ApkPath = $ApkPath
	Token = $Token
	Channel = 'release'
	Repo = "Ithan-Cassiano/Kosen-Releases"
}
if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
	$params.DescriptionFile = $DescriptionFile
} elseif (Test-Path (Join-Path $PSScriptRoot "..\release-notes\release\v$Version.md")) {
	$params.DescriptionFile = Join-Path $PSScriptRoot "..\release-notes\release\v$Version.md"
} else {
	$params.Description = $Description
}

& (Join-Path $PSScriptRoot "publish-release.ps1") @params
