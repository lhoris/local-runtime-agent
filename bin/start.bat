@echo off
rem Launches the Local Runtime Agent from a distribution layout (bin\, lib\, config\).
setlocal

set "APP_HOME=%~dp0.."
set "JAR="
for %%f in ("%APP_HOME%\lib\*.jar") do set "JAR=%%f"

if not defined JAR (
    echo No application jar found in %APP_HOME%\lib 1>&2
    exit /b 1
)

if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=prod"

java %JAVA_OPTS% -jar "%JAR%" --spring.config.additional-location="file:%APP_HOME%\config\"
