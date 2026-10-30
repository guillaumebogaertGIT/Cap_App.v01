function Get-CapDocker {
    $capCommand=Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($capCommand) {
        $env:PATH=(Split-Path $capCommand.Source -Parent)+';'+$env:PATH
        return $capCommand.Source
    }
    foreach ($capCandidate in @((Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'),'C:/Program Files/Docker/Docker/resources/bin/docker.exe')) {
        if (Test-Path -LiteralPath $capCandidate) {
            # Docker's credential helper lives beside the CLI after a fresh install.
            $env:PATH=(Split-Path $capCandidate -Parent)+';'+$env:PATH
            return $capCandidate
        }
    }
    throw 'Docker Desktop CLI not found. Open Docker Desktop, then reopen this terminal.'
}
