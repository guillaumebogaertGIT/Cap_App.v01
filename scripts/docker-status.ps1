param([switch]$Logs)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'docker-common.ps1')
$capDocker=Get-CapDocker
& $capDocker ps -a --filter 'label=com.docker.compose.project=cap-docker' --filter 'label=com.docker.compose.service=app' --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
if ($Logs) {
    $capIds=@(& $capDocker ps -aq --filter 'label=com.docker.compose.project=cap-docker' --filter 'label=com.docker.compose.service=app')
    foreach ($capId in $capIds) { if ($capId) { & $capDocker logs --tail 60 $capId } }
}
