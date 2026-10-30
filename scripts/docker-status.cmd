@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0docker-status.ps1" %*
exit /b %errorlevel%
