@echo off
chcp 65001 >nul
setlocal
set ACTION=%~1
if "%ACTION%"=="" set ACTION=start
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\local.ps1" -Action %ACTION%
exit /b %ERRORLEVEL%
