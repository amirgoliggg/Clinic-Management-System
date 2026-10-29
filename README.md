# 🏥 Enterprise Clinic Management System (v3.1.0)

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Security](https://img.shields.io/badge/Crypto-AES--256--GCM_%7C_HMAC--SHA256-0284C7?style=for-the-badge)
![Version](https://img.shields.io/badge/Release-v3.1.0-10B981?style=for-the-badge)

سامانه جامع، امن و تحت شبکه داخلی (**LAN/WLAN**) برای مدیریت کلینیک و مراکز درمانی، طراحی‌شده با معماری **Spring Boot 3**، رابط کاربری مدرن با نمودارهای زنده تحلیلی، رمزنگاری داده‌های حساس با استاندارد **AES-256-GCM** و زنجیره حسابرسی ضد دست‌کاری (**SHA-256 Merkle Chain + External Signed Anchor**).

---

## ✨ قابلیت‌های کلیدی نسخه ۳.۱ (What's New in v3.1)

* **🛡️ امنیت چندلایه و رمزنگاری پیشرفته:**
  * **رمزنگاری فیلدهای بالینی در دیتابیس (Data-at-Rest):** تمامی تشخیص‌ها، نسخه‌ها و یادداشت‌های پزشکی پیش از ذخیره در دیتابیس با الگوریتم `AES-256-GCM` رمزنگاری می‌شوند.
  * **زنجیره حسابرسی ضد دست‌کاری و مقاوم در برابر کرش:** مجهز به ژورنال دوفازی (`.pending`)، قفل هم‌زمانی چندپروسه‌ای (`ReentrantLock` + `FileLock`) و بازیابی خودکار در صورت قطع ناگهانی برق یا بسته شدن پروسه.
  * **فایروال لایه شبکه داخلی (`LanSecurityFilter`):** قفل شدن نشست کاربری روی IP ایستگاه کاری (Session-to-IP Binding) جهت جلوگیری از سرقت نشست در شبکه وای‌فای.
* **📊 داشبوردهای تحلیلی و ترازنامه مالی بدون ابهام:**
  * موتور رسم نمودارهای برداری (`SVG`) برای پایش جریان بیماران، گروه‌های خونی، رده سنی، ظرفیت پزشکان و سهم درگاه‌های پرداخت.
  * ترازنامه گام‌به‌گام حسابداری با تفکیک دقیق تعرفه پایه، تخفیف/سهم بیمه، مالیات، استردادهای نظارت‌شده (جزئی و کامل)، خالص موجودی خزانه و مطالبات باز.
  * پشتیبانی از کلید یکتای تراکنش (**Idempotency Key**) و قفل بدبینانه دیتابیس (`PESSIMISTIC_WRITE`) برای جلوگیری از پرداخت تکراری.
* **👥 مهندسی نقش‌ها (Custom Role Builder):**
  * امکان تعریف نقش‌های سازمانی جدید (مانند `PHARMACIST`، `LAB_TECH` و...) در لحظه توسط مدیر ارشد همراه با تعیین سطح مجوز پایه.
* **⚡ اجرای بدون تنظیمات (Zero-Config & Plug-and-Play):**
  * تولید خودکار کلیدهای ۲۵۶ بیتی در اولین اجرا و باز شدن خودکار پنجره اختصاصی نرم‌افزار بدون نیاز به تنظیمات دستی.

---

## 🚀 راه‌اندازی و کامپایل سریع (Quick Start)

این پروژه مجهز به **Maven Wrapper** است و نیازی به نصب دستی Maven ندارد (فقط کافی است **JDK 17** یا بالاتر روی سیستم نصب باشد).

### ۱. دریافت و اجرای مستقیم (ویندوز / لینوکس / مک)

```bash
git clone [https://github.com/amirgoliggg/Clinic-Management-System.git](https://github.com/amirgoliggg/Clinic-Management-System.git)
cd Clinic-Management-System