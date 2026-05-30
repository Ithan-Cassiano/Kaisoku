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
	Repo = "Ithan-Cassiano/Kosen-Releases"
}
if (-not [string]::IsNullOrWhiteSpace($DescriptionFile)) {
	$params.DescriptionFile = $DescriptionFile
} else {
	$params.Description = $Description
}

& (Join-Path $PSScriptRoot "publish-release.ps1") @params
