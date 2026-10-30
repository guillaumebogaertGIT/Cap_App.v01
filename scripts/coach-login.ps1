param([string]$Email='hello@cap-gistel.be')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-dev-secrets.ps1')
$capLocalSecretPath=Join-Path (Split-Path $PSScriptRoot -Parent) '.local/coach-secrets.xml'
$capValues=Read-CapLocalSecrets
$capKey='coach-cap-'+$Email.Trim().ToLowerInvariant()
if (!$capValues.Contains($capKey)) { throw 'No saved temporary password for this coach. Existing accounts keep their own passwords.' }
$capPassword=[Net.NetworkCredential]::new('', $capValues[$capKey]).Password
try {
    Set-Clipboard -Value $capPassword
    Write-Host ('Login email: '+$Email.Trim().ToLowerInvariant())
    Write-Host 'The saved temporary password is now on your clipboard. Paste it in the CAP password field. Clear your clipboard afterward. It stops working after the coach changes their password.'
    Start-Process 'http://127.0.0.1:8081/accounts/index.html' -WindowStyle Hidden
} finally { $capPassword=$null }
