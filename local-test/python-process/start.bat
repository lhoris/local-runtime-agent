@echo off
setlocal
cd /d "%~dp0"
rem Use the Python launcher so the WindowsApps python alias is not selected.
py -3 test_process.py
if errorlevel 1 (
    echo Python test process failed to start. errorlevel=%errorlevel%
    exit /b %errorlevel%
)
endlocal
