@echo off
setlocal
cd /d "%~dp0"
call stop.bat
timeout /t 1 /nobreak >nul
call run.bat
endlocal
