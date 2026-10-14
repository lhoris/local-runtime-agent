@echo off
setlocal
cd /d "%~dp0"

set "PYTHON_PID=%~1"
if not defined PYTHON_PID if exist ".agent.pid" set /p PYTHON_PID=<.agent.pid
if not defined PYTHON_PID (
    echo Python PID argument is required. Example: stop.bat 12345
    exit /b 1
)

echo Stopping test Python process PID %PYTHON_PID%...
taskkill /PID %PYTHON_PID% /T /F >nul 2>&1
if errorlevel 1 (
    echo Failed to stop Python process PID %PYTHON_PID%.
    exit /b %errorlevel%
)
del /q ".agent.pid" >nul 2>&1
endlocal
