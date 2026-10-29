# Creates/reuses only sandbox catalogue objects. Does not create customers or payments.
param([string]$ExpectedAccount = '')
$ErrorActionPreference = 'Stop'
function Invoke-CapStripe([string[]]$StripeArguments) {
    # Windows PowerShell treats harmless native stderr notices as errors with Stop.
    $capPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $capResponse = & stripe.cmd @StripeArguments --stripe-version '2025-06-30.basil' 2>$null; $capExit = $LASTEXITCODE }
    finally { $ErrorActionPreference = $capPreference }
    if ($capExit -ne 0) { throw ('Stripe TEST request failed at ' + $StripeArguments[0] + ' ' + $StripeArguments[1] + '. Check the selected CAP sandbox. No response body was printed.') }
    try { return ($capResponse -join "`n" | ConvertFrom-Json) }
    catch { throw 'Stripe returned an unreadable response. No response body was printed.' }
}
if ($env:STRIPE_API_KEY -and !$env:STRIPE_API_KEY.StartsWith('sk_test_') -and !$env:STRIPE_API_KEY.StartsWith('rk_test_')) { throw 'Only Stripe TEST keys are permitted.' }
$capAccount = Invoke-CapStripe @('get','/v1/account')
if (!$ExpectedAccount) {
    Write-Host 'In the CAP sandbox Dashboard, copy the account ID (acct_...) from the address bar.'
    $ExpectedAccount = Read-Host 'Expected CAP sandbox account ID (not a secret)'
}
if ($ExpectedAccount -notmatch '^acct_[A-Za-z0-9]+$' -or $capAccount.id -ne $ExpectedAccount) { throw 'Stripe CLI is not connected to the expected CAP sandbox. Nothing was created.' }
$capOffers = @(
    @('full_1m','CAP TEST - Full access 1 maand',9000,$false),
    @('full_3m','CAP TEST - Full access 3 maanden',22500,$false),
    @('full_12m','CAP TEST - Full access 1 jaar',75000,$false),
    @('monthly_flexible','CAP TEST - Full access maandelijks opzegbaar',7500,$true),
    @('monthly_fixed','CAP TEST - Full access 12 maanden vast',6500,$true),
    @('credits_1','CAP TEST - 1 lescredit',1800,$false),
    @('credits_5','CAP TEST - 5 lescredits',8000,$false),
    @('credits_10','CAP TEST - 10 lescredits',14000,$false),
    @('credits_20','CAP TEST - 20 lescredits',24000,$false)
)
$capPrices = [ordered]@{}
foreach ($capOffer in $capOffers) {
    $capLookup = 'cap_test_' + $capOffer[0] + '_v1'
    $capExisting = Invoke-CapStripe @('get','/v1/prices','-d',('lookup_keys[0]=' + $capLookup),'-d','limit=100')
    if ($capExisting.has_more -or @($capExisting.data).Count -gt 1) { throw 'Ambiguous test price lookup. Inspect the sandbox catalogue.' }
    if (@($capExisting.data).Count -eq 1) { $capPrice = $capExisting.data[0] }
    else {
        $capArguments = @('post','/v1/prices','--confirm','--idempotency',('cap-catalog-' + $capLookup),
            '-d',('lookup_key=' + $capLookup),'-d','currency=eur','-d',('unit_amount=' + $capOffer[2]),
            '-d',('product_data[name]=' + $capOffer[1]),'-d',('metadata[cap_offer]=' + $capOffer[0]))
        if ($capOffer[3]) { $capArguments += @('-d','recurring[interval]=month','-d','recurring[interval_count]=1') }
        $capPrice = Invoke-CapStripe $capArguments
    }
    if ($capPrice.livemode -ne $false -or !$capPrice.active -or $capPrice.currency -ne 'eur' -or $capPrice.unit_amount -ne $capOffer[2] -or $capPrice.billing_scheme -ne 'per_unit' -or $capPrice.transform_quantity) { throw 'Existing price does not match the confirmed CAP offer. Nothing was overwritten.' }
    if ($capOffer[3]) {
        if ($capPrice.recurring.interval -ne 'month' -or $capPrice.recurring.interval_count -ne 1) { throw 'Monthly price has unexpected recurrence.' }
    } elseif ($capPrice.recurring) { throw 'Prepaid memberships and credits must have one-time prices.' }
    $capPrices[$capOffer[0]] = $capPrice.id
    Write-Host ('Ready: ' + $capOffer[1])
}
$capConfigurations = Invoke-CapStripe @('get','/v1/billing_portal/configurations','-d','active=true','-d','limit=100')
if ($capConfigurations.has_more) { throw 'More portal configurations exist than can safely be inspected. Ask the developer to select one.' }
$capPortal = @($capConfigurations.data | Where-Object { $_.metadata.cap_catalog -eq 'v1' })
if ($capPortal.Count -gt 1) { throw 'Multiple CAP catalogue portals found. Inspect the sandbox settings.' }
if ($capPortal.Count -eq 0) {
    $capPortal = @(Invoke-CapStripe @('post','/v1/billing_portal/configurations','--confirm','--idempotency','cap-catalog-portal-v1',
        '-d','metadata[cap_catalog]=v1','-d','features[subscription_cancel][enabled]=false',
        '-d','features[subscription_update][enabled]=false','-d','features[payment_method_update][enabled]=true',
        '-d','features[invoice_history][enabled]=true','-d','features[customer_update][enabled]=false'))
}
if ($capPortal[0].livemode -ne $false -or $capPortal[0].features.subscription_cancel.enabled -or $capPortal[0].features.subscription_update.enabled -or $capPortal[0].features.subscription_pause.enabled) { throw 'Catalogue portal must disable cancellation, plan changes and pausing. Cancel the flexible plan inside CAP.' }
$capDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) '.local'
New-Item -ItemType Directory -Force -Path $capDirectory | Out-Null
[ordered]@{ account=$ExpectedAccount; prices=$capPrices; portal=$capPortal[0].id } | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $capDirectory 'stripe-test-catalog.json')
Write-Host 'CAP test catalogue is ready. Local file contains only object IDs, no secrets. Start scripts/start-stripe-test.cmd next.'
