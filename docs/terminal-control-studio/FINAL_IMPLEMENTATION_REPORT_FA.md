# گزارش پیاده‌سازی Terminal Control Studio Pro

## وضعیت فعلی

پیاده‌سازی Terminal Control Studio و چند اصلاح پایداری مستقیماً روی `main` ثبت شده‌اند، مطابق درخواست صریح کاربر. آخرین Commit ثبت‌شده در شروع این بازبینی `2521508` است؛ اصلاحات تازهٔ دسترس‌پذیری و تست‌های UI هنوز نیازمند اجرای Gateها و ثبت Commit هستند. تعداد Featureهای `VERIFIED_COMPLETE` برابر ۰ از ۱۱ است، چون معیار تکمیل هر Feature اجرای Gateهای UI/دستگاه را نیز می‌خواهد.

## تغییرات و علت‌ها

- نوار ثابت به مدل پروفایل/تم، ویرایشگر، ذخیره‌سازی JSON نسخه‌دار، اکشن‌های نوع‌دار، مدیریت Modifier، ژست و موتور ماکرو تبدیل شد.
- کلید `F…` در پروفایل Power متن خالی می‌فرستاد؛ به F1 عملی تغییر کرد و F1 تا F12 در کاتالوگ کلیدها قرار گرفت.
- تکرار نگه‌داشتن Backspace/Delete بعد از تکرار، یک کلیک اضافه در رهاسازی می‌فرستاد؛ کلیک رهاسازی پس از شروع تکرار حذف شد.
- Snippet از اکشن‌ها پشتیبانی می‌شد اما میانبر مستقیمی در پروفایل پیش‌فرض نداشت؛ کلید Snip اضافه و برای آن تست رگرسیون نوشته شد.
- CI به‌خاطر وابستگی به وجود متن `R.string.snippets_short` در فایل مشخص شکست می‌خورد؛ Gate حالا اتصال preset، dispatcher و Sheet را بررسی می‌کند. آپلود گزارش‌ها در صورت نبود گزارش هشدار می‌دهد تا شکست ثانویه علت اصلی را نپوشاند.

## راستی‌آزمایی

در این دور، `:app:testMarketDebugUnitTest`, `:app:compileMarketDebugKotlin`, `:app:compileMarketDebugAndroidTestKotlin`, `:app:lintMarketDebug` و `:app:assembleMarketDebug` با هم اجرا شدند و Build موفق شد. Unit Testها ۱٬۰۱۵ مورد در ۱۱۸ suite، بدون شکست/خطا/ردشدن، گزارش کردند. Lint خطای مسدودکننده نداشت؛ هشدار سازگاری AGP 8.13.2 با compileSdk 37 و چند هشدار deprecation در تست باقی است. APK خروجی Debug است، SHA-256 آن `d9b4030dc2c5f07bc40cb8b9f74c0a6864b29dc47f837bf9f3dfa97039249dbd` است و این محیط `apksigner` را در PATH نداشت؛ گواهی APK در این دور مستقلاً تأیید نشده است. AndroidTest فقط کامپایل شده و روی دستگاه اجرا نشده است. CI تازه برای تغییرات جاری هنوز باید پس از push بررسی شود.

## علت‌های تأییدشده و موارد نامشخص

شکست CI تأیید شد: Gate منسوخ Snippet قبل از تست/build متوقف می‌شد. دسترسی‌نداشتن مستقیم Snippet در پروفایل پیش‌فرض نیز نقص واقعی بود و اصلاح شد. در این مرحله هیچ Crash واقعی اپلیکیشن بازتولید نشده؛ تست دستگاه در دسترس نیست و بنابراین نبود Crash تأیید نمی‌شود.

## دستگاه و انتشار

`adb` در `/root/android-sdk/platform-tools` موجود است، اما هیچ Emulator یا دستگاهی متصل نیست. نصب تصویر Android 36 به‌دلیل کمتر از ۱ گیگابایت فضای آزاد متوقف شد؛ فضای موقت دانلود پاک شد. Compose UI، TalkBack، نصب روی دستگاه، ماتریس API و SSH/SFTP واقعی همچنان بررسی نشده‌اند. AGP 8.13.2 دربارهٔ compileSdk 37 هشدار سازگاری می‌دهد، هرچند Build موفق است.

وضعیت انتشار: **NOT VERIFIED**. APK موجود Debug و غیر Production است.

## مجوزها و متن‌باز بودن

کد خود Terminal SSH تحت Apache License 2.0 است؛ متن کامل آن در `LICENSE` قرار دارد. این مجوز به‌تنهایی مجوز اجزای ثالث را جایگزین نمی‌کند. فهرست و ارجاع مجوزهای وابستگی‌های اصلی در `NOTICE.md` آمده است: JSch 2.28.6 (Revised BSD و اعلان‌های اجزای همراه)، termlib 0.2.1 (Apache 2.0)، libvterm (MIT)، Bouncy Castle Provider 1.85 (MIT) و AndroidX/Jetpack Compose (Apache 2.0). پیش از انتشار هر APK/AAB باید گراف دقیق وابستگی‌های resolve‌شده و متن‌های attribution همان Artifact دوباره بررسی شوند.

کلیپ تبلیغاتی از بازسازی وفادار به سورس و داده‌های ساختگی استفاده می‌کند؛ تصاویر آن اسکرین‌شات زنده از APK نیستند. این موضوع و ارجاع مجوزها در `NOTICE.md` و فایل‌های تحویل ویدیو ثبت شده است.

## خروجی‌ها و وضعیت انتشار

- شاخهٔ کاری فعلی: `main` (تغییرات جدید محلی تا پایان Gateها commit نشده‌اند).
- آخرین Commit پوش‌شده در ابتدای این دور: `2521508`.
- PR مرتبط: https://github.com/ashkansuridr-bit/terminal/pull/4 (به‌دلیل پوش مستقیم، وضعیت وب آن را جداگانه بررسی کنید؛ PR معیار وضعیت شاخه نیست).
- اجرای CI: https://github.com/ashkansuridr-bit/terminal/actions/runs/38389187528
- APK تولیدی/امضاشده: تأیید نشده؛ خروجی Debug با گواهی Debug، نسخهٔ Production محسوب نمی‌شود.
- کلیپ معرفی فارسی: `TerminalSSH-Promo-fa.mp4` و `TerminalSSH-Promo-fa-no-bgm.mp4` در ریشه؛ جزئیات hash و محدودیت شناسایی موسیقی در `docs/terminal-control-studio/VIDEO_DELIVERY.md` و بازبینی مستقل در `VIDEO_REVIEW.md`.
- Checkpoint: `docs/terminal-control-studio/LOOP_CHECKPOINT.md`
