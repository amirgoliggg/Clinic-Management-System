#!/bin/bash

echo "==================================================="
echo "  درحال راه‌اندازی سامانه مدیریت درمانگاه..."
echo "==================================================="

# بررسی نصب بودن جاوا
if ! command -v java &> /dev/null; then
    echo "[خطا] جاوا یافت نشد! لطفاً JDK 17 یا بالاتر را نصب کنید."
    exit 1
fi

# باز کردن خودکار مرورگر بر اساس سیستم‌عامل
if [[ "$OSTYPE" == "darwin"* ]]; then
    # سیستم عامل مک
    (sleep 4 && open "http://localhost:8080") &
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # سیستم عامل لینوکس
    (sleep 4 && xdg-open "http://localhost:8080") &
fi

# اجرا با Maven Wrapper
chmod +x ./mvnw 2>/dev/null
if [ -f "./mvnw" ]; then
    ./mvnw spring-boot:run
else
    mvn spring-boot:run
fi