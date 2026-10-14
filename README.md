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

<div align="center">

![موشن Terminal SSH و آدرس GitHub پروژه](TerminalSSH-GitHub-Motion-Preview-fa.gif)

[تماشای فایل ویدیویی ۱۶:۹](TerminalSSH-GitHub-Motion-fa.mp4) · [دریافت موشن عمودی ۹:۱۶](TerminalSSH-GitHub-Motion-CTA-fa.mp4)

</div>

موشن بالا برای صفحهٔ GitHub ساخته شده و نام برنامه، نشان ترمینال، مجوز Apache 2.0 و نشانی کامل مخزن را نمایش می‌دهد. داخل خود برنامه هم فشردن کلیدهای نوار ترمینال بازخورد حرکتی و لمسی دارد و وضعیت اتصال با تغییر رنگ و ضربان ظریف مشخص می‌شود. سورس قابل‌بازسازی و مجوز فونت در [`promo/github-motion-asset/`](promo/github-motion-asset/) ثبت شده‌اند؛ MP4 و GIF رسانهٔ تبلیغاتی‌اند و تحت مجوز سورس برنامه عرضه نمی‌شوند.

[▶ تماشای کلیپ معرفی فارسی با موسیقی پس‌زمینه](terminal-promo-bgm-release.mp4) · [دریافت مستقیم فایل MP4](https://raw.githubusercontent.com/ashkansuridr-bit/terminal/main/terminal-promo-bgm-release.mp4)

این ویدیوی عمودیِ ۲۸ ثانیه‌ای، نگاهی کوتاه به تجربهٔ Terminal SSH دارد. رابط‌ها بازسازی وفادار به سورس هستند و داده‌های میزبان و ترمینال در ویدیو ساختگی‌اند؛ ویدیو ضبط زنده از APK نیست. فایل و جزئیات تولید در [گزارش تحویل و مجوز رسانه](docs/terminal-control-studio/VIDEO_DELIVERY.md) ثبت شده‌اند. موسیقی و افکت‌های ویدیو مجوزهای جدا از کد برنامه دارند؛ هویت دقیق قطعهٔ موسیقی در منبع موجود به‌طور مستقل مشخص نشده است.

### نسخهٔ بلند ۱۲۰ثانیه‌ای

[▶ تماشای معرفی کامل ۱۲۰ثانیه‌ای (عمودی ۹:۱۶، بی‌صدا)](TerminalSSH-Promo-fa-120s.mp4) · [دریافت مستقیم نسخهٔ ۱۲۰ثانیه‌ای](https://raw.githubusercontent.com/ashkansuridr-bit/terminal/main/TerminalSSH-Promo-fa-120s.mp4)

این نسخه چهار فصل دقیقاً ۳۰ثانیه‌ای دارد که به‌ترتیب اتصال SSH، میزبان‌ها، ترمینال و فایل‌های SFTP/متن‌باز را معرفی می‌کنند. [فصل ۱](promo/terminalssh-120s/chapters/TerminalSSH-Promo-fa-120s-01.mp4) · [فصل ۲](promo/terminalssh-120s/chapters/TerminalSSH-Promo-fa-120s-02.mp4) · [فصل ۳](promo/terminalssh-120s/chapters/TerminalSSH-Promo-fa-120s-03.mp4) · [فصل ۴](promo/terminalssh-120s/chapters/TerminalSSH-Promo-fa-120s-04.mp4). فیلم از قاب‌های بازسازی‌شده بر پایهٔ UI برنامه و داده‌های نمونهٔ ساختگی استفاده می‌کند؛ ضبط زنده از APK یا نشست SSH نیست. خروجی عمداً بی‌صداست. سورس قابل‌بازسازی، طرح نماها، فریم‌های بازبینی و مجوز فونت در [`promo/terminalssh-120s/`](promo/terminalssh-120s/) آمده‌اند. جزئیات فنی و مجوزها در [گزارش تحویل ویدیو](docs/terminal-control-studio/VIDEO_DELIVERY.md) است.

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

کد نسخهٔ `0.7.2` است (`versionCode` برابر 12). آخرین بررسی محلی 1,030 تست JVM را با موفقیت گذراند؛ کامپایل برنامه و تست‌های Android، ساخت APK و Lint نیز موفق بودند. Lint صفر خطا، 108 هشدار و 2 نکتهٔ راهنما گزارش کرد.

آخرین اجرای GitHub Actions شمارهٔ [38498546350](https://github.com/ashkansuridr-bit/terminal/actions/runs/38498546350) روی `f7e8ab9`، Verify را گذراند. چهار اجرای API26/36 در مجموع ۴۵۰ از ۴۵۶ تست را پاس کردند؛ چهار شکست مربوط به پیمایش کلید Snippets و دو شکست مربوط به پیمایش سوییچ امنیتی در API36 بود. Tab، Host Edit و گروه‌های Host List در این دور پاس شدند. API37 Debug/Preview پیش از شروع تست با `adb: device offline` و timeout بوت متوقف شدند؛ Release candidates هم اجرا نشدند. اصلاح بعدی helper پیمایش Snippets و گام اسکرول Settings را هدف گرفته و منتظر CI تازه است؛ شواهد در [ماتریس تست](docs/terminal-control-studio/TEST_MATRIX.md) ثبت شده است.

ابزار `adb` در محیط نصب است، اما AVD موجود به‌علت نبود فایل کرنل `kernel-ranchu` بوت نمی‌شود و دستگاه فیزیکی هم به این workspace متصل نیست. اتصال به سرور آزمایشی SSH/SFTP، اجرای TalkBack روی دستگاه، ماتریس کامل نسخه‌های اندروید و امضای تولیدی نیز تأیید نشده‌اند. به همین دلیل این پروژه را آمادهٔ انتشار عمومی معرفی نمی‌کنیم.

## APK آزمایشی

فایل [TerminalSSH-0.7.2-MarketDebug-TEST.apk](TerminalSSH-0.7.2-MarketDebug-TEST.apk) با `:app:assembleMarketDebug` از سورس تازه، شامل چیدمان چندردیفهٔ پروفایل‌ها، ساخته و با گواهی Debug اندروید امضا شده است (SHA-256: `bca914ad9d1cd4550b251b08670edb07c02f81d8c9ffc3c378acfb314e346f10`). این فایل فقط برای ارزیابی است؛ **امضای تولیدی ندارد و نسخهٔ انتشار فروشگاهی نیست**. شناسهٔ بسته و روش بررسی در [ANDROID_TEST_BUILD.md](ANDROID_TEST_BUILD.md) ثبت شده‌اند.

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
