# گزارش پیاده‌سازی Terminal Control Studio Pro

## وضعیت فعلی

پیاده‌سازی اصلی Terminal Control Studio روی شاخهٔ مستقل انجام شده و PR شمارهٔ ۴ باز است. آخرین تغییر، دسترسی مستقیم به Snippetها را به پروفایل پیش‌فرض Shell برگرداند و Gate متنی قدیمی CI را با بررسی سیم‌کشی واقعی اکشن جایگزین کرد. تغییرات ادغام نشده‌اند.

## تغییرات و علت‌ها

- نوار ثابت به مدل پروفایل/تم، ویرایشگر، ذخیره‌سازی JSON نسخه‌دار، اکشن‌های نوع‌دار، مدیریت Modifier، ژست و موتور ماکرو تبدیل شد.
- کلید `F…` در پروفایل Power متن خالی می‌فرستاد؛ به F1 عملی تغییر کرد و F1 تا F12 در کاتالوگ کلیدها قرار گرفت.
- تکرار نگه‌داشتن Backspace/Delete بعد از تکرار، یک کلیک اضافه در رهاسازی می‌فرستاد؛ کلیک رهاسازی پس از شروع تکرار حذف شد.
- Snippet از اکشن‌ها پشتیبانی می‌شد اما میانبر مستقیمی در پروفایل پیش‌فرض نداشت؛ کلید Snip اضافه و برای آن تست رگرسیون نوشته شد.
- CI به‌خاطر وابستگی به وجود متن `R.string.snippets_short` در فایل مشخص شکست می‌خورد؛ Gate حالا اتصال preset، dispatcher و Sheet را بررسی می‌کند. آپلود گزارش‌ها در صورت نبود گزارش هشدار می‌دهد تا شکست ثانویه علت اصلی را نپوشاند.

## راستی‌آزمایی

- MarketDebug JVM: تعداد ۱٬۰۱۳ تست در ۱۱۸ Suite؛ صفر شکست، خطا یا تست ردشده.
- کامپایل تست‌های Android: موفق؛ اجرای Instrumentation انجام نشده است.
- `source_audit.py`، `market_release_gate.py` و `loop2_gate.py`: موفق.
- تست‌های اعتبارسنجی Artifact: ۹ از ۹ موفق.
- MarketDebug APK و lint: موفق؛ lint بدون خطا، با ۱۰۸ هشدار و ۲ hint.
- APK با `apksigner` بررسی شد و گواهی Android Debug دارد؛ امضای Production تأیید نشده است.
- اجرای CI مربوط به `ead9c4f` در زمان این گزارش هنوز در حال اجراست.

## علت‌های تأییدشده و موارد نامشخص

شکست CI تأیید شد: Gate منسوخ Snippet قبل از تست/build متوقف می‌شد. دسترسی‌نداشتن مستقیم Snippet در پروفایل پیش‌فرض نیز نقص واقعی بود و اصلاح شد. در این مرحله هیچ Crash واقعی اپلیکیشن بازتولید نشده؛ تست دستگاه در دسترس نیست و بنابراین نبود Crash تأیید نمی‌شود.

## دستگاه و انتشار

`adb` در `/root/android-sdk/platform-tools` موجود است، اما هیچ Emulator یا دستگاهی متصل نیست. نصب تصویر Android 36 به‌دلیل کمتر از ۱ گیگابایت فضای آزاد متوقف شد؛ فضای موقت دانلود پاک شد. Compose UI، TalkBack، نصب روی دستگاه، ماتریس API و SSH/SFTP واقعی همچنان بررسی نشده‌اند. AGP 8.13.2 دربارهٔ compileSdk 37 هشدار سازگاری می‌دهد، هرچند Build موفق است.

وضعیت انتشار: **NOT VERIFIED**. APK موجود Debug و غیر Production است.

## خروجی‌ها

- شاخه: `feature/terminal-control-studio-pro`
- Commit پیاده‌سازی: `f2e7524`
- آخرین Commit اصلاح Gate و Snippet: `ead9c4f`
- PR: https://github.com/ashkansuridr-bit/terminal/pull/4
- MarketDebug APK: `app/build/outputs/apk/market/debug/app-market-debug.apk`
- SHA-256: `b19031080566d68ed25ffcc3ca14e5361b27d5b9497eaf68397691aa6bbe7f87`
- Checkpoint: `docs/terminal-control-studio/LOOP_CHECKPOINT.md`
