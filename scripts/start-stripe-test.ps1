# Local opt-in only. Secrets stay in this process and are restored when CAP stops.
$ErrorActionPreference = 'Stop'
$capStripeNames = 'CAP_STRIPE_TEST_ENABLED','CAP_STRIPE_TEST_SECRET_KEY','CAP_STRIPE_TEST_WEBHOOK_SECRET','CAP_STRIPE_TEST_PRICE_ID','CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID','CAP_STRIPE_TEST_BASE_URL','CAP_STRIPE_TEST_PRICES','CAP_TEST_PAID_BOOKINGS'
$capStripePrevious = @{}
foreach ($capStripeName in $capStripeNames) { $capStripePrevious[$capStripeName] = [Environment]::GetEnvironmentVariable($capStripeName, 'Process') }
try {
    Write-Host 'CAP Stripe TEST MODE only. Start Stripe CLI forwarding first (see docs/stripe-test-setup.md).'
    $capStripeSecret = Read-Host 'Stripe secret key starting sk_test_ (private)' -AsSecureString
    $env:CAP_STRIPE_TEST_SECRET_KEY = [System.Net.NetworkCredential]::new('', $capStripeSecret).Password
    if (!$env:CAP_STRIPE_TEST_SECRET_KEY.StartsWith('sk_test_')) { throw 'Only a Stripe TEST secret key is accepted. Live keys cannot be used.' }
    $capWebhookSecret = Read-Host 'Webhook signing secret printed by stripe listen (private)' -AsSecureString
    $env:CAP_STRIPE_TEST_WEBHOOK_SECRET = [System.Net.NetworkCredential]::new('', $capWebhookSecret).Password
    if (!$env:CAP_STRIPE_TEST_WEBHOOK_SECRET.StartsWith('whsec_')) { throw 'A webhook signing secret starting whsec_ is required.' }
    $capCatalogPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.local/stripe-test-catalog.json'
    if (Test-Path -LiteralPath $capCatalogPath) {
        $capCatalog = Get-Content -Raw -LiteralPath $capCatalogPath | ConvertFrom-Json
        $env:CAP_STRIPE_TEST_PRICES = $capCatalog.prices | ConvertTo-Json -Compress
        $env:CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID = $capCatalog.portal
        $env:CAP_STRIPE_TEST_PRICE_ID = Read-Host 'Old EUR10 TEST price ID, only if you want to keep testing that old subscription (Enter to skip)'
        $env:CAP_TEST_PAID_BOOKINGS = 'true'
        Write-Host 'CAP catalogue loaded. Group and Open gym bookings use a valid membership or one credit. Cancel before the start to return the credit.'
    } else {
        $env:CAP_STRIPE_TEST_PRICE_ID = Read-Host 'Existing TEST price ID for CAP Test Membership, 10 EUR/month (price_...)'
        $env:CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID = Read-Host 'Existing TEST portal configuration ID (bpc_...)'
        Write-Host 'New catalogue not configured yet. Run scripts/setup-stripe-catalog.cmd to enable the nine CAP offers.'
    }
    $env:CAP_STRIPE_TEST_BASE_URL = 'http://127.0.0.1:8081'
    $env:CAP_STRIPE_TEST_ENABLED = 'true'
    & (Join-Path $PSScriptRoot 'start-accounts.ps1')
} finally {
    foreach ($capStripeName in $capStripeNames) { [Environment]::SetEnvironmentVariable($capStripeName, $capStripePrevious[$capStripeName], 'Process') }
    $capStripeSecret = $null; $capWebhookSecret = $null
}
