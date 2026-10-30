@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0create-coach-accounts.ps1" %*
exit /b %errorlevel%
