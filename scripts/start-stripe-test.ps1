# Local opt-in only. Secrets stay in this process and are restored when CAP stops.
$ErrorActionPreference = 'Stop'
$capStripeNames = 'CAP_STRIPE_TEST_ENABLED','CAP_STRIPE_TEST_SECRET_KEY','CAP_STRIPE_TEST_WEBHOOK_SECRET','CAP_STRIPE_TEST_PRICE_ID','CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID','CAP_STRIPE_TEST_BASE_URL'
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
    $env:CAP_STRIPE_TEST_PRICE_ID = Read-Host 'Existing TEST price ID for CAP Test Membership, 10 EUR/month (price_...)'
    $env:CAP_STRIPE_TEST_PORTAL_CONFIGURATION_ID = Read-Host 'Existing TEST portal configuration ID (bpc_...)'
    $env:CAP_STRIPE_TEST_BASE_URL = 'http://127.0.0.1:8081'
    $env:CAP_STRIPE_TEST_ENABLED = 'true'
    & (Join-Path $PSScriptRoot 'start-accounts.ps1')
} finally {
    foreach ($capStripeName in $capStripeNames) { [Environment]::SetEnvironmentVariable($capStripeName, $capStripePrevious[$capStripeName], 'Process') }
    $capStripeSecret = $null; $capWebhookSecret = $null
}
