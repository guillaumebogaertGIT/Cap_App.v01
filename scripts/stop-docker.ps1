$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'docker-common.ps1')
$capDocker=Get-CapDocker
# Project/service labels scope this command; no volumes or native processes are touched.
$capIds=@(& $capDocker ps -aq --filter 'label=com.docker.compose.project=cap-docker' --filter 'label=com.docker.compose.service=app')
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable. Open Docker Desktop.' }
foreach ($capId in $capIds) {
    if ($capId) {
        & $capDocker stop $capId
        if ($LASTEXITCODE -ne 0) { throw 'CAP container could not be stopped.' }
        & $capDocker rm $capId
        if ($LASTEXITCODE -ne 0) { throw 'Stopped CAP container could not be removed.' }
    }
}
Write-Host 'CAP Docker stopped. PostgreSQL and its data are unchanged.'
