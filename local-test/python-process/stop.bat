@echo off
setlocal
cd /d "%~dp0"

if not exist "python_process.pid" (
    echo No test Python PID file found.
    exit /b 0
)

set /p PYTHON_PID=<python_process.pid
echo Stopping test Python process PID %PYTHON_PID%...
taskkill /PID %PYTHON_PID% /T /F >nul 2>&1
del /q "python_process.pid" >nul 2>&1
endlocal
