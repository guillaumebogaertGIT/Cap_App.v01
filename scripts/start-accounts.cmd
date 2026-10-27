@echo off
rem This applies only to the child PowerShell process, not the Windows policy.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-accounts.ps1" %*
exit /b %errorlevel%
