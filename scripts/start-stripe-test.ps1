# Local development only. Credentials are encrypted for this Windows user/PC.
param([switch]$RefreshCredentials, [switch]$UpdateWebhook, [switch]$ForgetCredentials,
    [ValidatePattern('^(|price_[A-Za-z0-9]+)$')][string]$LegacyPriceId = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'local-dev-secrets.ps1')
if ($ForgetCredentials) { Remove-CapLocalSecrets; return }
$capStripeNames = 'CAP_STRIPE_TEST_ENABLED','CAP_STRIPE_TEST_SECRET_KEY','CAP_STRIPE_TEST_WEBHOOK_SECRET','CAP_STRIPE_TEST_PRICE_ID','CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID','CAP_STRIPE_TEST_BASE_URL','CAP_STRIPE_TEST_PRICES','CAP_TEST_PAID_BOOKINGS'
$capStripePrevious = @{}
foreach ($capStripeName in $capStripeNames) { $capStripePrevious[$capStripeName] = [Environment]::GetEnvironmentVariable($capStripeName, 'Process') }
try {
    Write-Host 'CAP Stripe TEST MODE only. Start Stripe CLI forwarding first (see docs/stripe-test-setup.md).'
    Write-Host 'Credentials are remembered with Windows encryption on this PC, outside Git. Use -RefreshCredentials to replace them or -ForgetCredentials to remove them.'
    $capStripeSecret = Get-CapLocalSecret -Name 'stripe-test-api' -Prompt 'Stripe secret key starting sk_test_ (private)' -Pattern '^sk_test_[A-Za-z0-9]+$' -Refresh:$RefreshCredentials
    $env:CAP_STRIPE_TEST_SECRET_KEY = [System.Net.NetworkCredential]::new('', $capStripeSecret).Password
    if (!$env:CAP_STRIPE_TEST_SECRET_KEY.StartsWith('sk_test_')) { throw 'Only a Stripe TEST secret key is accepted. Live keys cannot be used.' }
    Save-CapLocalSecret -Name 'stripe-test-api' -Value $capStripeSecret
    $capWebhookSecret = Get-CapLocalSecret -Name 'stripe-test-webhook' -Prompt 'Webhook signing secret printed by stripe listen (private)' -Pattern '^whsec_[A-Za-z0-9]+$' -Refresh:($RefreshCredentials -or $UpdateWebhook)
    $env:CAP_STRIPE_TEST_WEBHOOK_SECRET = [System.Net.NetworkCredential]::new('', $capWebhookSecret).Password
    if (!$env:CAP_STRIPE_TEST_WEBHOOK_SECRET.StartsWith('whsec_')) { throw 'A webhook signing secret starting whsec_ is required.' }
    Save-CapLocalSecret -Name 'stripe-test-webhook' -Value $capWebhookSecret
    $capCatalogPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.local/stripe-test-catalog.json'
    if (Test-Path -LiteralPath $capCatalogPath) {
        $capCatalog = Get-Content -Raw -LiteralPath $capCatalogPath | ConvertFrom-Json
        $env:CAP_STRIPE_TEST_PRICES = $capCatalog.prices | ConvertTo-Json -Compress
        $env:CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID = $capCatalog.portal
        $env:CAP_STRIPE_TEST_PRICE_ID = $LegacyPriceId
        $env:CAP_TEST_PAID_BOOKINGS = 'true'
        Write-Host 'CAP catalogue loaded. Group and Open gym bookings use a valid membership or one credit. Cancel before the start to return the credit.'
    } else {
        $env:CAP_STRIPE_TEST_PRICE_ID = Read-Host 'Existing TEST price ID for CAP Test Membership, 10 EUR/month (price_...)'
        $env:CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID = Read-Host 'Existing TEST portal configuration ID (bpc_...)'
        Write-Host 'New catalogue not configured yet. Run scripts/setup-stripe-catalog.cmd to enable the nine CAP offers.'
    }
    $env:CAP_STRIPE_TEST_BASE_URL = 'http://127.0.0.1:8081'
    $env:CAP_STRIPE_TEST_ENABLED = 'true'
    & (Join-Path $PSScriptRoot 'start-accounts.ps1') -RefreshCredentials:$RefreshCredentials
} finally {
    foreach ($capStripeName in $capStripeNames) { [Environment]::SetEnvironmentVariable($capStripeName, $capStripePrevious[$capStripeName], 'Process') }
    $capStripeSecret = $null; $capWebhookSecret = $null
}
