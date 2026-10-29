@echo off
rem Applies only to this child process; no machine execution-policy change.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-stripe-test.ps1" %*
exit /b %errorlevel%
