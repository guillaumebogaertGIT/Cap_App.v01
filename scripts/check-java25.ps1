# Run from any directory: powershell -File scripts/check-java25.ps1
# Diagnostic only: does not change JAVA_HOME, PATH, or Windows settings.
$ErrorActionPreference = 'Stop'
try {
    if ($env:JAVA_HOME) {
        $javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
        $javacExecutable = Join-Path $env:JAVA_HOME 'bin/javac.exe'
        if (!(Test-Path $javaExecutable) -or !(Test-Path $javacExecutable)) {
            throw 'JAVA_HOME must point to a JDK containing bin/java.exe and bin/javac.exe.'
        }
    } else {
        $javaExecutable = (Get-Command java -ErrorAction Stop).Source
        $javacExecutable = (Get-Command javac -ErrorAction Stop).Source
    }
    # Version commands write to stderr on some JDKs.
    $ErrorActionPreference = 'Continue'
    $javaOutput = & $javaExecutable -version 2>&1
    $javaExit = $LASTEXITCODE
    $javacOutput = & $javacExecutable -version 2>&1
    $javacExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($javaExit -ne 0 -or $javacExit -ne 0) { throw 'Java version checks failed.' }
    if (($javaOutput -join ' ') -notmatch 'version "25(?:[."]|-)' -or
        ($javacOutput -join ' ') -notmatch 'javac 25(?:[.\s-]|$)') {
        throw 'CAP backend requires JDK 25; the selected Java runtime/compiler is different.'
    }
    Write-Host ($javaOutput -join [Environment]::NewLine)
    Write-Host ($javacOutput -join [Environment]::NewLine)
    Write-Host 'JDK 25 setup OK. From backend, run .\mvnw.cmd --version and .\mvnw.cmd clean test.'
} catch {
    Write-Host "Setup check failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'See README: set JAVA25_HOME to your JDK 25 folder, restart VS Code, and open a new terminal.'
    Write-Host 'Outside VS Code, select JAVA_HOME and PATH for the session as documented in README.'
    exit 1
}
