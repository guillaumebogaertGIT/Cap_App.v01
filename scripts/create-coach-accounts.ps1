# Explicit local development provisioning. Never resets passwords or promotes existing athletes.
param(
    [ValidateSet('cap','cap_test')][string]$Database='cap',
    [ValidatePattern('^[a-zA-Z_][a-zA-Z0-9_]*$')][string]$DatabaseUser='cap',
    [ValidateRange(1024,65535)][int]$Port=5432
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-dev-secrets.ps1')
$capRoot=Split-Path $PSScriptRoot -Parent
$capPrevious=@{}
foreach ($capKey in @('JAVA_HOME','CAP_DATABASE_URL','CAP_DATABASE_USER','CAP_DATABASE_PASSWORD')) {
    $capPrevious[$capKey]=[Environment]::GetEnvironmentVariable($capKey,'Process')
}
try {
    $env:JAVA_HOME=Join-Path $env:USERPROFILE '.jdks/openjdk-25/jdk-25.0.2'
    Push-Location (Join-Path $capRoot 'backend')
    try {
        & .\mvnw.cmd -q -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/coach-classpath.txt' '-DincludeScope=runtime'
        if ($LASTEXITCODE -ne 0) { throw 'Could not compile the local account tool. No accounts changed.' }
    } finally { Pop-Location }
    $capSecret=Get-CapLocalSecret -Name "database-127.0.0.1-$Port-$Database-$DatabaseUser" -Prompt "Password for PostgreSQL role $DatabaseUser"
    $env:CAP_DATABASE_URL="jdbc:postgresql://127.0.0.1:$Port/$Database"
    $env:CAP_DATABASE_USER=$DatabaseUser
    $env:CAP_DATABASE_PASSWORD=[Net.NetworkCredential]::new('', $capSecret).Password
    # Keep coach handover passwords separate from resettable Stripe/database credentials.
    $capLocalSecretPath=Join-Path $capRoot '.local/coach-secrets.xml'
    $capStaff=@(
        @{email='hello@cap-gistel.be';name='Glenn Vlamynck'},
        @{email='dieter.vlamynck@gmail.com';name='Dieter Vlamynck'},
        @{email='mmarchand@hotmail.be';name='Mathias Marchand'},
        @{email='marliesvermeesch@hotmail.com';name='Marlies Vermeersch'},
        @{email='Robinvgeluwe@gmail.com'.ToLowerInvariant();name='Robin Van geluwe'},
        @{email='vince.decraemer@hotmail.com';name='Vince Decraemer'}
    )
    foreach ($capCoach in $capStaff) {
        $capSaved=Read-CapLocalSecrets
        $capCredential="coach-$Database-$($capCoach.email)"
        if (!$capSaved.Contains($capCredential)) {
            $capBytes=New-Object byte[] 24
            $capRandom=[Security.Cryptography.RandomNumberGenerator]::Create()
            try { $capRandom.GetBytes($capBytes) } finally { $capRandom.Dispose() }
            $capTemporary=ConvertTo-SecureString ([Convert]::ToBase64String($capBytes)) -AsPlainText -Force
            # Save before creation so a lost response cannot lose a newly created account's password.
            Save-CapLocalSecret -Name $capCredential -Value $capTemporary
            $capSaved=Read-CapLocalSecrets
        }
        $capCoach.password=[Net.NetworkCredential]::new('', $capSaved[$capCredential]).Password
    }
    $capClasspath=(Join-Path $capRoot 'backend/target/classes')+';'+(Get-Content -Raw (Join-Path $capRoot 'backend/target/coach-classpath.txt')).Trim()
    $capPayload=ConvertTo-Json -InputObject $capStaff -Compress
    $capPayload | & (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $capClasspath be.cap.backend.accounts.CoachProvisioner
    if ($LASTEXITCODE -ne 0) { throw 'No batch was committed. Existing accounts and passwords were preserved.' }
    Write-Host 'Coach setup complete. CREATED accounts use separate Windows-encrypted temporary passwords. KEPT accounts keep their existing passwords.'
    Write-Host 'To log in as Glenn locally: .\scripts\coach-login.cmd'
} finally {
    $capPayload=$null; $capStaff=$null; $capSecret=$null
    foreach ($capKey in $capPrevious.Keys) { [Environment]::SetEnvironmentVariable($capKey,$capPrevious[$capKey],'Process') }
}
