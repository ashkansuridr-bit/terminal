<div align="center">

# Terminal SSH

### کلاینت SSH و SFTP برای اندروید، با پشتیبانی از فارسی و رابط راست‌به‌چپ

[![Android CI](https://github.com/ashkansuridr-bit/terminal/actions/workflows/android-release.yml/badge.svg)](https://github.com/ashkansuridr-bit/terminal/actions/workflows/android-release.yml)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84?logo=android&logoColor=white)](#سازگاری-و-ساخت)
[![Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**نسخهٔ سورس: 0.7.2 · وضعیت انتشار: تأییدنشده**

[دریافت APK آزمایشی](TerminalSSH-0.7.2-MarketDebug-TEST.apk) ·
[گزارش ساخت APK](ANDROID_TEST_BUILD.md) ·
[گزارش پیاده‌سازی و وضعیت تست‌ها](docs/terminal-control-studio/FINAL_IMPLEMENTATION_REPORT_FA.md)

</div>

---

## کلیپ معرفی برنامه

[▶ تماشای کلیپ معرفی فارسی با موسیقی پس‌زمینه](terminal-promo-bgm-release.mp4) · [دریافت مستقیم فایل MP4](https://raw.githubusercontent.com/ashkansuridr-bit/terminal/main/terminal-promo-bgm-release.mp4)

این ویدیوی عمودیِ ۲۸ ثانیه‌ای، نگاهی کوتاه به تجربهٔ Terminal SSH دارد. رابط‌ها بازسازی وفادار به سورس هستند و داده‌های میزبان و ترمینال در ویدیو ساختگی‌اند؛ ویدیو ضبط زنده از APK نیست. فایل و جزئیات تولید در [گزارش تحویل و مجوز رسانه](docs/terminal-control-studio/VIDEO_DELIVERY.md) ثبت شده‌اند. موسیقی و افکت‌های ویدیو مجوزهای جدا از کد برنامه دارند؛ هویت دقیق قطعهٔ موسیقی در منبع موجود به‌طور مستقل مشخص نشده است.

---

## معرفی

Terminal SSH یک برنامهٔ اندرویدی برای اتصال SSH، استفاده از ترمینال و انتقال فایل با SFTP است. برنامه رابط فارسی و راست‌به‌چپ دارد و زبان‌های انگلیسی، عربی، اسپانیایی، فرانسوی و روسی را نیز در منابع پروژه فراهم می‌کند.

این مخزن شامل سورس برنامه، اسکریپت‌های بررسی و ساخت، مستندات و خروجی آزمایشی اندروید است. قابلیت‌های فهرست‌شده در این README از روی سورس بررسی شده‌اند؛ اجرای آن‌ها روی همهٔ دستگاه‌ها یا سرورهای SSH به‌معنای تأیید عملی نیست.

## قابلیت‌های اصلی

- اتصال SSH با گذرواژه یا کلید خصوصی و بررسی کلید میزبان؛ تغییر کلید شناخته‌شده باعث رد اتصال می‌شود.
- نشست‌های ترمینال چندگانه و ترمینال محلی.
- مرور فایل‌ها و انتقال از طریق SFTP، با صف انتقال و عملیات رایج فایل.
- نوار کلیدهای ویژه و پروفایل‌های قابل ویرایش؛ کارهای در حال توسعهٔ Terminal Control Studio در [ماتریس قابلیت‌ها](docs/terminal-control-studio/FEATURE_MATRIX.md) ثبت می‌شوند.
- نگه‌داری رمزگذاری‌شدهٔ داده‌های حساس برنامه و ابزارهای ورود/خروج تنظیمات.
- رابط چندزبانه با منابع فارسی، انگلیسی، عربی، اسپانیایی، فرانسوی و روسی.

جزئیات دقیق وضعیت هر قابلیت در [گزارش نهایی پیاده‌سازی](docs/terminal-control-studio/FINAL_IMPLEMENTATION_REPORT_FA.md) و [وضعیت Loop](docs/terminal-control-studio/LOOP_STATE.json) آمده است.

## وضعیت فعلی و محدودیت راستی‌آزمایی

کد نسخهٔ `0.7.2` است (`versionCode` برابر 12). آخرین بررسی محلی 1,026 تست JVM را با موفقیت گذراند؛ کامپایل برنامه و تست‌های Android، ساخت APK و Lint نیز موفق بودند. Lint صفر خطا، 106 هشدار و 2 نکتهٔ راهنما گزارش کرد.

آخرین اجرای کامل GitHub Actions شمارهٔ [38452720047](https://github.com/ashkansuridr-bit/terminal/actions/runs/38452720047) روی `ab331c7`، گیت Verify را گذراند. API26 Debug/Preview هرکدام ۳ شکست از ۱۱۴ تست و API36 Debug/Preview هرکدام ۲ شکست از ۱۱۴ داشتند. در API26 شکست‌های HostList و SettingsToggle و کلید Tab باقی ماند؛ در API36 شکست‌های HostList و SettingsToggle باقی ماندند. API37 Debug/Preview پیش از اجرای تست با خطای `adb: device offline` و timeout بوت متوقف شدند. اصلاحات تازهٔ HostList، SettingsToggle و تست دسترسی‌پذیری Tab هنوز به اجرای CI نیاز دارند. جزئیات در [ماتریس تست](docs/terminal-control-studio/TEST_MATRIX.md) آمده است.

محیط محلی ابزار `adb` یا دستگاه متصل نداشت. اتصال به سرور آزمایشی SSH/SFTP، اجرای TalkBack روی دستگاه، ماتریس کامل نسخه‌های اندروید و امضای تولیدی نیز تأیید نشده‌اند. به همین دلیل این پروژه را آمادهٔ انتشار عمومی معرفی نمی‌کنیم.

## APK آزمایشی

فایل [TerminalSSH-0.7.2-MarketDebug-TEST.apk](TerminalSSH-0.7.2-MarketDebug-TEST.apk) با `:app:assembleMarketDebug` ساخته و با گواهی Debug اندروید امضا شده است (SHA-256: `01e0409dae251ccfaf1656c956f86165dbef1040e14dd9261e0ad1d54e404aec`). این فایل فقط برای ارزیابی است؛ **امضای تولیدی ندارد و نسخهٔ انتشار فروشگاهی نیست**. شناسهٔ بسته و روش بررسی در [ANDROID_TEST_BUILD.md](ANDROID_TEST_BUILD.md) ثبت شده‌اند.

## ساخت از سورس

پیش‌نیازها: JDK 17، Android SDK Platform 37 و Android Build Tools سازگار با پروژه. برای فهرست دقیق نسخه‌های Gradle و پلاگین‌ها، فایل‌های ریشه و `app/build.gradle.kts` را ببینید.

```sh
git clone https://github.com/ashkansuridr-bit/terminal.git
cd terminal
./gradlew :app:testMarketDebugUnitTest
./gradlew :app:compileMarketDebugKotlin :app:compileMarketDebugAndroidTestKotlin
./gradlew :app:lintMarketDebug :app:assembleMarketDebug
```

APK توسعه در `app/build/outputs/apk/market/debug/` ساخته می‌شود. برای آزمون‌های Instrumentation به Emulator یا دستگاه اندرویدی سازگار نیاز دارید؛ موفقیت کامپایل تست‌ها به‌تنهایی به‌معنی اجرای آن‌ها نیست.

## امنیت و حریم خصوصی

برنامه از بررسی کلید میزبان SSH استفاده می‌کند و برای دور زدن هشدار تغییر کلید، اعتبارسنجی را خاموش نمی‌کند. اعتبارنامه‌ها و Snippetهای برنامه از مسیر Vault و Android Keystore نگه‌داری می‌شوند. این توضیحات جایگزین بازبینی امنیتی مستقل یا آزمون دستگاه نیستند.

لطفاً آسیب‌پذیری امنیتی را به‌صورت عمومی در Issue منتشر نکنید؛ روش تماس و سیاست گزارش‌دهی در [SECURITY.md](SECURITY.md) آمده است. سیاست‌های حریم خصوصی: [فارسی](store/PRIVACY_POLICY_FA.md) و [English](store/PRIVACY_POLICY_EN.md).

## متن‌باز و مجوزها

سورس خود پروژه تحت [Apache License 2.0](LICENSE) منتشر شده است. این مجوز به‌طور خودکار مجوز کتابخانه‌ها، فونت‌ها، موسیقی یا ویدیوهای همراه را تغییر نمی‌دهد؛ هر جزء می‌تواند شرایط جداگانه داشته باشد. نام و مجوز وابستگی‌های ثبت‌شده در [NOTICE.md](NOTICE.md) آمده است. پیش از بازتوزیع برنامه، متن مجوزها و اعلان‌های لازم را همراه خروجی نگه دارید.

مشارکت‌ها باید با [راهنمای مشارکت](CONTRIBUTING.md) و مجوز فایل‌های مربوط سازگار باشند. برای گزارش خطا یا پیشنهاد، از [GitHub Issues](https://github.com/ashkansuridr-bit/terminal/issues) استفاده کنید.

## مستندات

- [نقشهٔ راه](docs/ROADMAP.md)
- [وضعیت قابلیت‌ها](docs/features/FEATURE_STATUS.json)
- [ماتریس Terminal Control Studio](docs/terminal-control-studio/FEATURE_MATRIX.md)
- [Checkpoint ادامهٔ کار](docs/terminal-control-studio/LOOP_CHECKPOINT.md)
- [گزارش مجوزهای وابستگی](NOTICE.md)

---

Copyright 2026 Ashkan Suri. مجوز سورس: Apache-2.0؛ مجوز اجزای ثالث تابع اعلان‌های جداگانهٔ آن‌هاست.
