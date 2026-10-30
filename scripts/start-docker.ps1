# Local Docker Desktop app + existing Windows PostgreSQL. Never migrates/copies the database.
param([switch]$Build,[switch]$WithoutPayments,[switch]$RefreshCredentials,[switch]$UpdateWebhook,
    [ValidateRange(1024,65535)][int]$HttpPort=8081)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-dev-secrets.ps1')
. (Join-Path $PSScriptRoot 'docker-common.ps1')
$capRoot=Split-Path $PSScriptRoot -Parent
$capDocker=Get-CapDocker
$capPreviousConfig=[Environment]::GetEnvironmentVariable('CAP_DOCKER_RUNTIME_CONFIG','Process')
$capPreviousPort=[Environment]::GetEnvironmentVariable('CAP_DOCKER_HTTP_PORT','Process')
try {
    $capOs=& $capDocker info --format '{{.OSType}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or $capOs -ne 'linux') { throw 'Open Docker Desktop and wait for the Linux engine to be running.' }
    # Refuse an occupied port without stopping the native backend or another app.
    $capSocket=[Net.Sockets.TcpClient]::new()
    try {
        try { $capSocket.ConnectAsync('127.0.0.1',$HttpPort).Wait(500) | Out-Null } catch { }
        if ($capSocket.Connected) { throw "Port $HttpPort is in use. Stop the existing CAP backend with Ctrl+C, or stop CAP Docker with scripts/stop-docker.cmd, then retry. No process was stopped." }
    } finally { $capSocket.Dispose() }
    $capImage=& $capDocker image ls --quiet cap-local:dev
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect local Docker images.' }
    $capNeedsBuild=!$capImage
    if ($Build -or $capNeedsBuild) {
        & $capDocker build --tag cap-local:dev --file (Join-Path $capRoot 'Dockerfile') $capRoot
        if ($LASTEXITCODE -ne 0) { throw 'Docker image build failed. No database was replaced.' }
    }
    $capDbSecret=Get-CapLocalSecret -Name 'database-127.0.0.1-5432-cap-cap' -Prompt 'Password for PostgreSQL role cap' -Refresh:$RefreshCredentials
    $capConfig=@{
        'spring.datasource.url'='jdbc:postgresql://host.docker.internal:5432/cap';
        'spring.datasource.username'='cap'; 'spring.datasource.password'=[Net.NetworkCredential]::new('', $capDbSecret).Password;
        'cap.billing.enabled'=(!$WithoutPayments).ToString().ToLowerInvariant();
        'cap.billing.base-url'="http://127.0.0.1:$HttpPort";
        'cap.accounts.membership-reviewers'=''; 'cap.accounts.finance-reviewer'=''
        'cap.accounts.staff-manager'=''
    }
    $capReviewPath=Join-Path $capRoot '.local/membership-reviewers.json'
    if (Test-Path -LiteralPath $capReviewPath) {
        $capEmails=@((Get-Content -Raw -LiteralPath $capReviewPath | ConvertFrom-Json).emails)
        if ($capEmails.Count -gt 2 -or @($capEmails | Where-Object { $_ -isnot [string] -or $_ -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$' }).Count) { throw 'Invalid membership reviewer configuration.' }
        $capConfig['cap.accounts.membership-reviewers']=$capEmails -join ','
    }
    $capFinancePath=Join-Path $capRoot '.local/finance-reviewer.json'
    if (Test-Path -LiteralPath $capFinancePath) {
        $capEmail=(Get-Content -Raw -LiteralPath $capFinancePath | ConvertFrom-Json).email
        if ($capEmail -isnot [string] -or $capEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') { throw 'Invalid finance reviewer configuration.' }
        $capConfig['cap.accounts.finance-reviewer']=$capEmail
    }
    $capStaffPath=Join-Path $capRoot '.local/staff-manager.json'
    if (Test-Path -LiteralPath $capStaffPath) {
        $capStaffEmail=(Get-Content -Raw -LiteralPath $capStaffPath | ConvertFrom-Json).email
        if ($capStaffEmail -isnot [string] -or $capStaffEmail -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') { throw 'Invalid staff manager configuration.' }
        $capConfig['cap.accounts.staff-manager']=$capStaffEmail
    }
    if (!$WithoutPayments) {
        $capCatalogPath=Join-Path $capRoot '.local/stripe-test-catalog.json'
        if (!(Test-Path -LiteralPath $capCatalogPath)) { throw 'Stripe test catalogue missing. Run scripts/setup-stripe-catalog.cmd, or use -WithoutPayments explicitly.' }
        $capCatalog=Get-Content -Raw -LiteralPath $capCatalogPath | ConvertFrom-Json
        $capApiSecret=Get-CapLocalSecret -Name 'stripe-test-api' -Prompt 'Stripe TEST secret key (private)' -Pattern '^sk_test_[A-Za-z0-9]+$' -Refresh:$RefreshCredentials
        $capHookSecret=Get-CapLocalSecret -Name 'stripe-test-webhook' -Prompt 'Stripe listener signing secret (private)' -Pattern '^whsec_[A-Za-z0-9]+$' -Refresh:($RefreshCredentials -or $UpdateWebhook)
        $capConfig['cap.billing.secret']=[Net.NetworkCredential]::new('', $capApiSecret).Password
        $capConfig['cap.billing.webhook-secret']=[Net.NetworkCredential]::new('', $capHookSecret).Password
        $capConfig['cap.billing.portal']=$capCatalog.portal
        $capConfig['cap.billing.prices']=$capCatalog.prices | ConvertTo-Json -Compress
        $capConfig['cap.billing.enforce-bookings']='true'
        if ($capConfig['cap.billing.secret'] -cnotmatch '^sk_test_[A-Za-z0-9]+$' -or $capConfig['cap.billing.webhook-secret'] -cnotmatch '^whsec_[A-Za-z0-9]+$') { throw 'Only Stripe TEST credentials are accepted.' }
    }
    # JSON is valid YAML. Compose mounts this as a runtime secret, not container env/build args.
    $env:CAP_DOCKER_RUNTIME_CONFIG=$capConfig | ConvertTo-Json -Compress
    $env:CAP_DOCKER_HTTP_PORT="$HttpPort"
    & $capDocker compose -f (Join-Path $capRoot 'compose.docker.yaml') up -d --no-build --force-recreate --wait --wait-timeout 120
    if ($LASTEXITCODE -ne 0) { throw 'CAP Docker did not become healthy. Check scripts/docker-status.cmd. The existing database was not replaced; no temporary database was started.' }
    Save-CapLocalSecret -Name 'database-127.0.0.1-5432-cap-cap' -Value $capDbSecret
    if (!$WithoutPayments) {
        Save-CapLocalSecret -Name 'stripe-test-api' -Value $capApiSecret
        Save-CapLocalSecret -Name 'stripe-test-webhook' -Value $capHookSecret
    }
    Write-Host "CAP Docker is ready: http://127.0.0.1:$HttpPort/app/index.html"
    Write-Host 'Same Windows PostgreSQL database, accounts and passwords. Docker Desktop and PostgreSQL must stay running.'
    Write-Host 'Payments are TEST ONLY. Keep scripts/stripe-listen.cmd running for checkout updates (default port 8081).'
    Write-Host 'Stop: scripts/stop-docker.cmd. After source changes: stop, then scripts/start-docker.cmd -Build.'
} finally {
    $env:CAP_DOCKER_RUNTIME_CONFIG=$capPreviousConfig
    $env:CAP_DOCKER_HTTP_PORT=$capPreviousPort
    $capConfig=$null; $capDbSecret=$null; $capApiSecret=$null; $capHookSecret=$null
}
