param(
	[string]$Token = $env:GH_TOKEN
)

$ErrorActionPreference = 'Stop'

if (-not [string]::IsNullOrWhiteSpace($Token)) {
	return $Token
}

$credInput = @"
protocol=https
host=github.com

"@
$cred = $credInput | git credential fill
foreach ($line in $cred) {
	if ($line -like 'password=*') {
		return $line.Substring(9)
	}
}

Write-Error "Token GitHub ausente. Use: `$env:GH_TOKEN='seu_token'"
