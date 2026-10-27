# Run from the repository root: .\scripts\start-accounts.ps1
# Passwords are prompted locally, passed only through process environment, then restored.
param(
    [ValidatePattern('^[a-zA-Z_][a-zA-Z0-9_]*$')][string]$Database = 'cap',
    [ValidatePattern('^[a-zA-Z_][a-zA-Z0-9_]*$')][string]$DatabaseUser = 'cap',
    [ValidateRange(1024,65535)][int]$Port = 5432
)
$ErrorActionPreference = 'Stop'
$capRoot = Split-Path $PSScriptRoot -Parent
$capNames = 'CAP_DATABASE_URL','CAP_DATABASE_USER','CAP_DATABASE_PASSWORD','CAP_COACH_EMAIL','CAP_COACH_PASSWORD','PGPASSWORD','PGCONNECT_TIMEOUT','JAVA_HOME'
$capPrevious = @{}
foreach ($capName in $capNames) { $capPrevious[$capName] = [Environment]::GetEnvironmentVariable($capName, 'Process') }
try {
    $capListener = [System.Net.Sockets.TcpClient]::new()
    try {
        $capConnect = $capListener.ConnectAsync('127.0.0.1', 8081)
        try { $capConnect.Wait(500) | Out-Null } catch { }
        if ($capListener.Connected) { throw 'Port 8081 is already in use. Stop the existing CAP backend with Ctrl+C in its terminal, then run scripts/start-accounts.cmd again. No process was stopped by this launcher.' }
    } finally { $capListener.Dispose() }
    $capProjectJdk = Join-Path $env:USERPROFILE '.jdks/openjdk-25/jdk-25.0.2'
    if (Test-Path (Join-Path $capProjectJdk 'bin/java.exe')) { $env:JAVA_HOME = $capProjectJdk }
    $capPsql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
    if (!$capPsql) {
        $capPsql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (!$capPsql) { throw 'PostgreSQL client not found. Install PostgreSQL, then follow docs/accounts-setup.md.' }
    $capSecret = Read-Host "Password for PostgreSQL role $DatabaseUser (not your CAP login)" -AsSecureString
    $env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $capSecret).Password
    $env:PGCONNECT_TIMEOUT = '5'
    $capConnection = @('-X','-w','-h','127.0.0.1','-p',"$Port",'-U',$DatabaseUser,'-d',$Database,'-v','ON_ERROR_STOP=1')
    $capResult = & $capPsql @capConnection -At -c 'SELECT current_database();' 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Cannot connect to PostgreSQL at 127.0.0.1:$Port/$Database as $DatabaseUser. Check the service, database, role and password in pgAdmin. No fallback database was started." }
    Write-Host "Connected to persistent local database: 127.0.0.1:$Port/$Database (role $DatabaseUser)."
    $capTable = & $capPsql @capConnection -At -c "SELECT to_regclass('public.cap_user');"
    if ($LASTEXITCODE -ne 0) { throw 'Database inspection failed; backend was not started.' }
    $capCoachCount = 0
    if ($capTable) {
        $capCoachCount = & $capPsql @capConnection -At -c "SELECT COUNT(*) FROM public.cap_user WHERE role='COACH';"
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect existing coaches; backend was not started.' }
    }
    if ([int]$capCoachCount -eq 0) {
        $env:CAP_COACH_EMAIL = Read-Host 'Email for the first CAP coach'
        $capCoachSecret = Read-Host 'Choose the first coach password (12-50 characters)' -AsSecureString
        $env:CAP_COACH_PASSWORD = [System.Net.NetworkCredential]::new('', $capCoachSecret).Password
    } else {
        $env:CAP_COACH_EMAIL = ''; $env:CAP_COACH_PASSWORD = ''
        Write-Host 'Existing coach accounts kept. Log in with your existing CAP credentials.'
    }
    $env:CAP_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$Port/$Database"
    $env:CAP_DATABASE_USER = $DatabaseUser
    $env:CAP_DATABASE_PASSWORD = $env:PGPASSWORD
    $env:PGPASSWORD = $null
    Write-Host 'Starting accounts mode on http://127.0.0.1:8081/app/index.html. Flyway applies pending migrations without resetting data.'
    Push-Location (Join-Path $capRoot 'backend')
    try {
        & '.\mvnw.cmd' spring-boot:run '-Dspring-boot.run.profiles=accounts'
        if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. Check the preceding migration/connection message. No temporary database was substituted.' }
    } finally { Pop-Location }
} finally {
    foreach ($capName in $capNames) { [Environment]::SetEnvironmentVariable($capName, $capPrevious[$capName], 'Process') }
    $capSecret = $null; $capCoachSecret = $null; $capResult = $null
}
