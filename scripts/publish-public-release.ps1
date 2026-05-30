param(
	[Parameter(Mandatory = $true)]
	[string]$Version,
	[string]$ApkPath = "",
	[string]$Token = ""
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

& (Join-Path $PSScriptRoot "publish-release.ps1") `
	-Version $Version `
	-ApkPath $ApkPath `
	-Token $Token `
	-Repo "Ithan-Cassiano/Kosen-Releases"
