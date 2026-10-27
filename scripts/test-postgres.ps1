param(
    [string]$DatabaseUrl = $env:CAP_TEST_POSTGRES_URL,
    [string]$DatabaseUser = $env:CAP_TEST_POSTGRES_USER
)
$ErrorActionPreference = 'Stop'
# Deliberately refuse ordinary CAP/production databases. Tests create fresh schemas.
if ($DatabaseUrl -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]+/cap_test[a-zA-Z0-9_]*(\?.*)?$') {
    throw 'Set CAP_TEST_POSTGRES_URL to a dedicated local cap_test database. This check never falls back to H2 and never uses the ordinary cap database.'
}
if (!$DatabaseUser) { throw 'Set CAP_TEST_POSTGRES_USER to the dedicated test database role.' }
$capPreviousRequired = $env:CAP_REQUIRE_POSTGRES_TESTS
$capPreviousUrl = $env:CAP_TEST_POSTGRES_URL
$capPreviousUser = $env:CAP_TEST_POSTGRES_USER
$capPreviousJava = $env:JAVA_HOME
try {
    $env:CAP_REQUIRE_POSTGRES_TESTS = 'true'
    $env:CAP_TEST_POSTGRES_URL = $DatabaseUrl
    $env:CAP_TEST_POSTGRES_USER = $DatabaseUser
    $capProjectJdk = Join-Path $env:USERPROFILE '.jdks/openjdk-25/jdk-25.0.2'
    if (Test-Path (Join-Path $capProjectJdk 'bin/java.exe')) { $env:JAVA_HOME = $capProjectJdk }
    Push-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'backend')
    try {
        & '.\mvnw.cmd' test
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL verification failed. See target/surefire-reports; no temporary fallback was used.' }
    } finally { Pop-Location }
} finally {
    $env:CAP_REQUIRE_POSTGRES_TESTS = $capPreviousRequired
    $env:CAP_TEST_POSTGRES_URL = $capPreviousUrl
    $env:CAP_TEST_POSTGRES_USER = $capPreviousUser
    $env:JAVA_HOME = $capPreviousJava
}
