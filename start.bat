@echo off
rem Run the executable Agent JAR from the copied project directory.
setlocal

rem Keep Korean application logs readable in the Windows console.
chcp 65001 >nul

rem Set this to the Java executable installed on the VM.
set "JAVA_EXE=D:\ws\JDK\jdk-21.0.2\bin\java.exe"
set "PROJECT_HOME=%~dp0"
set "JAR="

for %%f in ("%PROJECT_HOME%target\local-runtime-agent-*.jar") do (
    if /I not "%%~xf"==".original" set "JAR=%%f"
)

if not exist "%JAVA_EXE%" (
    echo Java executable was not found: %JAVA_EXE% 1>&2
    echo Edit JAVA_EXE at the top of run-agent.bat. 1>&2
    exit /b 1
)

if not defined JAR (
    echo Executable Agent JAR was not found in %PROJECT_HOME%target 1>&2
    exit /b 1
)

set "CONFIG_ARG="
if exist "%PROJECT_HOME%config\" set "CONFIG_ARG=--spring.config.additional-location=file:%PROJECT_HOME%config\"

echo Starting Local Runtime Agent...
echo Java: %JAVA_EXE%
echo JAR:  %JAR%
"%JAVA_EXE%" %JAVA_OPTS% -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%JAR%" %CONFIG_ARG% %*
set "EXIT_CODE=%ERRORLEVEL%"
echo Local Runtime Agent stopped with exit code %EXIT_CODE%.
exit /b %EXIT_CODE%
