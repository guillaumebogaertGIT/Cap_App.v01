@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-docker.ps1" %*
exit /b %errorlevel%
