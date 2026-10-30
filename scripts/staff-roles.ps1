param([string]$MappingFile='.local/staff-role-mapping.json',[switch]$ApplyApproved)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'local-dev-secrets.ps1')
$capRoot=Split-Path $PSScriptRoot -Parent
$capMapping=(Resolve-Path -LiteralPath (Join-Path $capRoot $MappingFile)).Path
$capJar=Join-Path $capRoot 'backend/target/rbac-build/backend-0.0.1-SNAPSHOT.jar'
if(!(Test-Path -LiteralPath $capJar)){throw 'Build the reviewed backend JAR first. See docs/staff-rbac.md. This command never starts or migrates CAP.'}
if($ApplyApproved){Write-Host 'Explicit approved activation requested. Stop CAP and take a verified backup first.'}
else {Write-Host 'Read-only mapping preview. No migration or role update will be applied.'}
$capPrevious=@{}
foreach($capName in @('CAP_DATABASE_URL','CAP_DATABASE_USER','CAP_DATABASE_PASSWORD')){$capPrevious[$capName]=[Environment]::GetEnvironmentVariable($capName,'Process')}
try {
    $capValues=Read-CapLocalSecrets
    if(!$capValues.Contains('database-127.0.0.1-5432-cap-cap')){throw 'No saved database credential. Configure local CAP first; never paste it into Git.'}
    $env:CAP_DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/cap';$env:CAP_DATABASE_USER='cap'
    $env:CAP_DATABASE_PASSWORD=[Net.NetworkCredential]::new('', $capValues['database-127.0.0.1-5432-cap-cap']).Password
    $capJava=Join-Path $env:USERPROFILE '.jdks/openjdk-25/jdk-25.0.2/bin/java.exe'
    if(!(Test-Path -LiteralPath $capJava)){$capJava='java.exe'}
    $capArguments=@('-Dloader.main=be.cap.backend.accounts.StaffRoleProvisioner','-cp',$capJar,'org.springframework.boot.loader.launch.PropertiesLauncher',$capMapping)
    if($ApplyApproved){$capArguments+='--apply-approved'}
    & $capJava @capArguments
    if($LASTEXITCODE -ne 0){throw 'Role mapping verification/activation failed. No credentials were printed.'}
} finally {
    foreach($capName in $capPrevious.Keys){[Environment]::SetEnvironmentVariable($capName,$capPrevious[$capName],'Process')}
    $capValues=$null
}
