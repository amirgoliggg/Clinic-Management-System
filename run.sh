#!/usr/bin/env bash

# هدایت به پوشه پروژه
cd "$(dirname "$0")" || exit 1

echo "==================================================="
echo "  Starting Clinic Management System (macOS / Linux)"
echo "==================================================="

# ۱. بررسی نصب جاوا
if ! command -v java &> /dev/null; then
    echo ""
    echo "[ERROR] Java is not installed or not in PATH!"
    echo "Please install JDK 17 or higher."
    echo ""
    exit 1
fi

# ۲. جستجوی فایل JAR در پوشه target
JAR_FILE=$(find target -maxdepth 1 -name "*.jar" 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo ""
    echo "[ERROR] No jar file found inside target folder!"
    echo ""
    echo "You must build the project first:"
    echo "1. In IntelliJ: Maven tab -> Lifecycle -> Double click 'package'"
    echo "   OR"
    echo "2. In Terminal run: ./mvnw clean package -DskipTests"
    echo ""
    exit 1
fi

echo "Found application: $JAR_FILE"
echo "Starting server and opening browser..."
echo "==================================================="
echo ""

# ۳. باز کردن خودکار مرورگر (پشتیبانی مجزا از مک و لینوکس)
if [[ "$OSTYPE" == "darwin"* ]]; then
    # مک
    (sleep 3 && open "http://localhost:8080") &
elif [[ "$OSTYPE" == "linux"* ]]; then
    # لینوکس
    (sleep 3 && command -v xdg-open >/dev/null && xdg-open "http://localhost:8080") &
fi

# ۴. اجرای برنامه
java -jar "$JAR_FILE"