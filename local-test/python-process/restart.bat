@echo off
setlocal
cd /d "%~dp0"
call stop.bat
timeout /t 3 /nobreak >nul
call start.bat
endlocal
