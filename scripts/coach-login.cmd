@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0coach-login.ps1" %*
exit /b %errorlevel%
