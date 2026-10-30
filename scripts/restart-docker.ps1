param([switch]$Build,[switch]$WithoutPayments,[switch]$RefreshCredentials,[switch]$UpdateWebhook,
    [ValidateRange(1024,65535)][int]$HttpPort=8081)
$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'stop-docker.ps1')
& (Join-Path $PSScriptRoot 'start-docker.ps1') -Build:$Build -WithoutPayments:$WithoutPayments -RefreshCredentials:$RefreshCredentials -UpdateWebhook:$UpdateWebhook -HttpPort $HttpPort
