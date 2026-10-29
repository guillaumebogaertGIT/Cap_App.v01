# Fake credentials only. Tests the Windows encryption and prompt-free restart path.
param([string]$ReadFixturePath = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-dev-secrets.ps1')
if ($ReadFixturePath) {
    $capLocalSecretPath = $ReadFixturePath
    $capRestored = Get-CapLocalSecret -Name 'stripe-test-api' -Prompt 'Must not prompt'
    if ([System.Net.NetworkCredential]::new('', $capRestored).Password -ne 'sk_test_CAPFAKE') { throw 'Cross-process reuse failed.' }
    Write-Host 'Cross-process encrypted reuse passed.'
    return
}
$capTestRoot = Join-Path (Split-Path $PSScriptRoot -Parent) ('backend/target/dev-launcher-' + [Guid]::NewGuid().ToString('N'))
$capTestScripts = Join-Path $capTestRoot 'scripts'
New-Item -ItemType Directory -Force -Path $capTestScripts | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'local-dev-secrets.ps1') -Destination $capTestScripts
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-stripe-test.ps1') -Destination $capTestScripts
$capLocalSecretPath = Join-Path $capTestRoot '.local/dev-secrets.xml'
function Assert-CapTest([bool]$Passed,[string]$Message) { if (!$Passed) { throw $Message } }
try {
    Save-CapLocalSecret 'stripe-test-api' (ConvertTo-SecureString 'sk_test_CAPFAKE' -AsPlainText -Force)
    Save-CapLocalSecret 'stripe-test-webhook' (ConvertTo-SecureString 'whsec_CAPFAKE' -AsPlainText -Force)
    Save-CapLocalSecret 'database-127.0.0.1-5432-cap-cap' (ConvertTo-SecureString 'CAP-fake-database' -AsPlainText -Force)
    $capXml = Get-Content -Raw -LiteralPath $capLocalSecretPath
    Assert-CapTest (!$capXml.Contains('sk_test_CAPFAKE') -and !$capXml.Contains('whsec_CAPFAKE') -and !$capXml.Contains('CAP-fake-database')) 'Plaintext found in credential file.'
    Assert-CapTest ($capXml.Contains('<SS')) 'SecureString encryption missing.'
    $capAcl = Get-Acl -LiteralPath $capLocalSecretPath
    Assert-CapTest $capAcl.AreAccessRulesProtected 'Credential file inherited broad permissions.'
    $capOwner = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    foreach ($capRule in $capAcl.Access) { Assert-CapTest ($capRule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -eq $capOwner) 'Another identity has access to saved credentials.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $PSCommandPath -ReadFixturePath $capLocalSecretPath
    Assert-CapTest ($LASTEXITCODE -eq 0) 'Child-process decryption failed.'
    # A complete Stripe launcher restart must reuse the keys and restore its environment.
    '{"prices":{"credits_5":"price_CAPFAKE"},"portal":"bpc_CAPFAKE"}' | Set-Content -LiteralPath (Join-Path $capTestRoot '.local/stripe-test-catalog.json')
    @'
param([switch]$RefreshCredentials)
if ($env:CAP_STRIPE_TEST_SECRET_KEY -ne 'sk_test_CAPFAKE' -or $env:CAP_STRIPE_TEST_WEBHOOK_SECRET -ne 'whsec_CAPFAKE') { throw 'Saved keys not passed to child launcher.' }
if ($env:CAP_TEST_PAID_BOOKINGS -ne 'true' -or $env:CAP_STRIPE_TEST_PRICE_ID) { throw 'Catalog defaults failed.' }
'@ | Set-Content -LiteralPath (Join-Path $capTestScripts 'start-accounts.ps1')
    function Read-Host { throw 'Unexpected credential prompt during saved restart.' }
    $capBeforeApi = $env:CAP_STRIPE_TEST_SECRET_KEY
    $capBeforeWebhook = $env:CAP_STRIPE_TEST_WEBHOOK_SECRET
    & (Join-Path $capTestScripts 'start-stripe-test.ps1')
    Assert-CapTest ($env:CAP_STRIPE_TEST_SECRET_KEY -eq $capBeforeApi -and $env:CAP_STRIPE_TEST_WEBHOOK_SECRET -eq $capBeforeWebhook) 'Launcher failed to restore process environment.'
    Remove-Item Function:Read-Host
    # Invalid input is retried; whitespace around copied Stripe keys is removed.
    $script:capReadCount = 0
    function Read-Host {
        param($Prompt,[switch]$AsSecureString)
        $script:capReadCount++
        $capFakeInput = if ($script:capReadCount -eq 1) { 'sk_live_REJECT' } else { '  sk_test_REPLACEMENT  ' }
        return ConvertTo-SecureString $capFakeInput -AsPlainText -Force
    }
    $capNew = Get-CapLocalSecret -Name 'stripe-test-api' -Prompt 'Fake input' -Pattern '^sk_test_[A-Za-z0-9]+$' -Refresh
    Assert-CapTest ($script:capReadCount -eq 2 -and [System.Net.NetworkCredential]::new('', $capNew).Password -eq 'sk_test_REPLACEMENT') 'Refresh/validation failed.'
    Remove-Item Function:Read-Host
    # Corrupt stores fail safely; forgetting removes only this fixture file.
    'not-credential-xml' | Set-Content -LiteralPath $capLocalSecretPath
    $capRejected = $false
    try { Read-CapLocalSecrets | Out-Null } catch { $capRejected = $_.Exception.Message.StartsWith('Saved CAP development credentials could not be decrypted.') }
    Assert-CapTest $capRejected 'Corrupt credentials were accepted.'
    Remove-CapLocalSecrets
    Assert-CapTest (!(Test-Path -LiteralPath $capLocalSecretPath)) 'Forget did not remove fixture credentials.'
    Write-Host 'PASS: encryption, ACL, restart reuse, environment restoration, input validation and forgetting.'
} finally {
    # Only files created by this test; no recursive deletion or real credential access.
    if (Test-Path -LiteralPath $capLocalSecretPath) { Remove-Item -LiteralPath $capLocalSecretPath -Force }
    if (Test-Path Function:Read-Host) { Remove-Item Function:Read-Host }
}
