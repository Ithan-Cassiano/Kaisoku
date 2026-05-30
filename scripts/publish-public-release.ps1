param(
	[Parameter(Mandatory = $true)]
	[string]$Version,
	[Parameter(Mandatory = $true)]
	[string]$Description,
	[string]$ApkPath = "",
	[string]$Token = ""
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Token)) {
	$Token = & (Join-Path $PSScriptRoot "Get-GitHubToken.ps1")
}

& (Join-Path $PSScriptRoot "publish-release.ps1") `
	-Version $Version `
	-Description $Description `
	-ApkPath $ApkPath `
	-Token $Token `
	-Repo "Ithan-Cassiano/Kosen-Releases"
