@echo off
chcp 65001 >nul
title سامانه جامع درمانگاه

echo ===================================================
echo   درحال آماده‌سازی و اجرای سامانه درمانگاه...
echo ===================================================

:: بررسی نصب بودن جاوا
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [خطا] جاوا روی سیستم شما یافت نشد! لطفاً JDK 17 یا بالاتر را نصب کنید.
    pause
    exit /b
)

:: اجرای برنامه با Maven Wrapper (نیازی به نصب دستی Maven نیست)
start http://localhost:8080
if exist "mvnw.cmd" (
    call mvnw.cmd spring-boot:run
) else (
    mvn spring-boot:run
)

pause