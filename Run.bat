@echo off
title Clinic Management System
cd /d "%~dp0"

echo ===================================================
echo   Starting Clinic Management System...
echo ===================================================

set "APP_JAR="
for %%f in (target\*.jar) do set "APP_JAR=%%f"

if "%APP_JAR%"=="" goto NO_JAR

echo Found application: %APP_JAR%
echo Starting server...
echo.

start http://localhost:8080
java -jar "%APP_JAR%"

pause
exit /b

:NO_JAR
echo.
echo [ERROR] No jar file found inside target folder!
echo.
echo You must build the project first:
echo 1. Open IntelliJ
echo 2. Click Maven tab on the right side
echo 3. Open Lifecycle
echo 4. Double click on package
echo.
pause
exit /b