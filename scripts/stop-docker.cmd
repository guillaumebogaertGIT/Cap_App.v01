@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-docker.ps1"
exit /b %errorlevel%
