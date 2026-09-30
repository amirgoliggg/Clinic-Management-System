# 🏥 سامانه هوشمند مدیریت جامع درمانگاه و بیمارستان
### Clinic Management System (CMS) — نسخه بتای سوم (Beta v3.0)

[![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6.x-6DB33F?style=for-the-badge&logo=spring-security&logoColor=white)](https://spring.io/projects/spring-security)
[![Database](https://img.shields.io/badge/H2_Database-Embedded-blue?style=for-the-badge&logo=database)](https://www.h2database.com/)
[![Version](https://img.shields.io/badge/Release-v3.0--Beta-purple?style=for-the-badge)]()
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)]()

یک پلتفرم یکپارچه، امن و چندلایه مبتنی بر اکوسیستم جاوا و اسپرینگ برای اتوماسیون کامل چرخه پذیرش، درمان، ترخیص، امور مالی و نظارت امنیتی درمانگاه‌ها با قابلیت اثبات ضدجعل بودن داده‌ها.

---

## 🌟 مهم‌ترین تغییرات و ویژگی‌های نسخه بتای سوم (Beta v3.0)

- 🔐 **پیاده‌سازی احراز هویت و کنترل دسترسی بر اساس نقش (RBAC):** تفکیک کامل سطوح دسترسی میان نقش‌های `SUPER_ADMIN`، `DOCTOR`، `CASHIER`، `NURSE` و `RECEPTIONIST`.
- 🔗 **مرکز مانیتورینگ امنیتی و اثبات یکپارچگی (SHA-256 Tamper-Proof Audit Chain):** ثبت تمامی تراکنش‌ها به صورت بلاک‌چینی با قابلیت راستی‌آزمایی ریاضی زنده جهت جلوگیری از هرگونه دستکاری در دیتابیس.
- 💳 **اتوماسیون مالی و تسویه هوشمند:** اصلاح محاسبه مانده بدهی بیمار بلافاصله پس از ثبت نسخه پزشک و قابلیت استعلام آنلاین و تسویه ریالی/دلاری.
- 💉 **پشتیبانی کامل از تمامی گروه‌های خونی:** پوشش هر ۸ گروه خونی اصلی (مثبت و منفی) در فرایند پذیرش.
- 🎨 **داشبورد تعاملی (SPA):** رابط کاربری مدرن با دارک‌مود، بدون وابستگی به فریم‌ورک‌های سنگین خارجی با واکنش‌گرایی بالا و کنسول رخداد لحظه‌ای.

---

## 🏗️ معماری سیستم و لایه‌بندی مدل‌ها

مدل‌های داده‌ای پروژه بر اساس اصول شی‌گرایی و با استراتژی ارث‌بری `InheritanceType.JOINED` در JPA پیاده‌سازی شده‌اند:

```text
               +-------------------+
               |      Person       |
               +-------------------+
                 /               \
                /                 \
     +-----------------+    +-----------------+
     |      Staff      |    |     Patient     |
     +-----------------+    +-----------------+
       /    |     \   \
      /     |      \   +---------------+
+-------+ +--------+ +---------+ +-----------+
| Admin | | Doctor | | Cashier | | Nurse /   |
+-------+ +--------+ +---------+ | Reception |
                                 +-----------+