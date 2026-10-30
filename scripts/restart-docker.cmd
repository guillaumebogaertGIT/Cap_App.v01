@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0restart-docker.ps1" %*
exit /b %errorlevel%
