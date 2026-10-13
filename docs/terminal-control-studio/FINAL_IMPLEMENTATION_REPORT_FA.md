# گزارش پیاده‌سازی Terminal Control Studio Pro

## وضعیت فعلی — آخرین به‌روزرسانی 2026-10-13

تغییرات Terminal Control Studio مستقیماً روی `main` ثبت می‌شوند، مطابق دستور صریح مالک مخزن. آخرین commit پوش‌شده `7727c4f` است؛ Verify، unit tests، lint و Debug builds موفق‌اند، اما اجرای Instrumentation شمارهٔ [38448985782](https://github.com/ashkansuridr-bit/terminal/actions/runs/38448985782) در API26 Debug/Preview چهار تست و API36 Debug/Preview سه تست را از ۱۱۴ مورد ناموفق گزارش کرد (۱۴ اجرای ناموفق). HostEdit، HostList و SettingsToggle در API26/36 و TerminalKeyboard در API26 شکست خوردند؛ Snippet موفق بود. دو job API37 پیش از تست به علت `adb: device offline` و timeout بوت Emulator متوقف شدند. harness follow-up و ساخت/ویرایش Theme سفارشی و تکثیر پروفایل محلی کامپایل و JVM-tested شده‌اند، اما هنوز runtime CI ندارند. شمار Featureهای `VERIFIED_COMPLETE` برابر ۰ از ۱۱ است؛ وضعیت انتشار همچنان NOT VERIFIED.

## تغییرات و علت‌ها

- نوار ثابت به مدل پروفایل/تم، ویرایشگر، ذخیره‌سازی JSON نسخه‌دار، اکشن‌های نوع‌دار، مدیریت Modifier، ژست و موتور ماکرو تبدیل شد.
- کلید `F…` در پروفایل Power متن خالی می‌فرستاد؛ به F1 عملی تغییر کرد و F1 تا F12 در کاتالوگ کلیدها قرار گرفت.
- تکرار نگه‌داشتن Backspace/Delete بعد از تکرار، یک کلیک اضافه در رهاسازی می‌فرستاد؛ کلیک رهاسازی پس از شروع تکرار حذف شد.
- Snippet از اکشن‌ها پشتیبانی می‌شد اما میانبر مستقیمی در پروفایل پیش‌فرض نداشت؛ کلید Snip اضافه و برای آن تست رگرسیون نوشته شد.
- ویرایشگر ژست‌ها فقط اتصال‌های موجود را نشان می‌داد؛ اکنون هر شش حرکت را با گزینه‌های سازگارِ موجود در پروفایل تنظیم یا پاک می‌کند و تغییرها در مسیر Undo/Redo/Save باقی می‌مانند. ژست‌های مبهم، Enter مستقیم، متن چندخطی و ماکروهای دارای Enter از انتخاب‌گر کنار گذاشته شده‌اند.
- واردکردن/خارج‌کردن تنظیمات از SAF به رابط ویرایشگر متصل شد. Import با سقف ۱ MiB، UTF-8 سخت‌گیرانه، خواندن غیرمسدودکننده، پیش‌نمایش parsed candidate و تأیید صریح جایگزینی کل تنظیمات انجام می‌شود. لغو انتخاب فایل تغییری اعمال نمی‌کند و پس از Import موفق ویرایشگر بسته می‌شود تا draft کهنه دادهٔ واردشده را بازنویسی نکند.
- حین همین اتصال دو نقص مستقل پیدا و اصلاح شد: مخزن تنظیمات پیش از موفقیت ذخیره‌سازی state را منتشر می‌کرد؛ اکنون اول persist می‌کند و سپس state را منتشر می‌کند (NEW-001). Parser JSON نیز عمق تو‌در‌تویی را محدود نمی‌کرد؛ اکنون بیش از ۶۴ سطح را رد می‌کند تا ورودی بدساخت به StackOverflow منجر نشود (NEW-002).
- بازبینی شکست‌های instrumentation یک ایراد دسترس‌پذیری دیگر را تأیید کرد: برچسب قابل‌دیدن تب ناوبری دوباره از `contentDescription` والد اعلام می‌شد. توضیح تکراری حذف شد (NEW-003)؛ compile و lint پاس شده‌اند اما تأیید runtime منتظر CI تازه است.
- CI به‌خاطر وابستگی به وجود متن `R.string.snippets_short` در فایل مشخص شکست می‌خورد؛ Gate حالا اتصال preset، dispatcher و Sheet را بررسی می‌کند. آپلود گزارش‌ها در صورت نبود گزارش هشدار می‌دهد تا شکست ثانویه علت اصلی را نپوشاند.

## راستی‌آزمایی

در workspace فعلی، JVM tests برابر ۱٬۰۲۵/۱٬۰۲۵ در ۱۱۹ suite، کامپایل MarketDebug برنامه و AndroidTest، و lint با صفر خطا (۱۰۶ هشدار، ۲ hint) موفق شدند. APK آزمایشی موجود از کد برنامهٔ قدیمی‌تر `cf0ac9d` ساخته و با گواهی Android Debug امضا شده؛ SHA-256: `36a954e19c2a9ae5831e2dd22c1ea196a0d72483cff9e0e6fd447fae924e98ae`. امضای Production تأیید نشده است.

Run `38448985782` روی commit تستی `7727c4f`، Verify را با موفقیت گذراند. هرکدام از API26 Debug/Preview و API36 Debug/Preview به‌ترتیب ۴/۴/۳/۳ شکست از ۱۱۴ داشتند (۱۴ اجرای ناموفق): HostEdit، HostList و SettingsToggle در هر دو API؛ TerminalKeyboard تنها در API26. Snippet موفق شد. تست‌های font slider و palette در این اجرا موفق شدند. API37 Debug/Preview هر دو با `adb: device offline` و boot timeout پیش از تست متوقف شدند. گزارش‌های HTML برای API26/36 در artifactهای workflow هستند؛ root causeها و harness follow-up در `LOOP_CHECKPOINT.md` ثبت شده‌اند. هیچ Crash واقعی در این دور بازتولید نشده است؛ نبود Crash تأیید نشده.

در workspace فعلی ۱٬۰۲۵ تست JVM، کامپایل برنامه و AndroidTest، و Lint با صفر خطا موفق‌اند؛ `loop2_gate.py`, `market_release_gate.py` و `git diff --check` نیز موفق‌اند. تغییرهای تازه هنوز uncommitted هستند و runtime CI تازه ندارند. محیط محلی `adb` ندارد و AVD API36 به علت نبود `kernel-ranchu` اجرا نمی‌شود؛ در نتیجه UI، TalkBack و device matrix را نمی‌توان محلی تأیید کرد.

## علت‌های تأییدشده و موارد نامشخص

شکست CI قبلی Gate منسوخ Snippet بود؛ اتصال مستقیم Snippet به پروفایل پیش‌فرض و Gate مربوطه اصلاح شده‌اند. شکست‌های تازهٔ API26/API36 assertionهای واقعی تست رابط کاربری‌اند و هنوز به‌عنوان رفع‌شده علامت نخورده‌اند. در این مرحله هیچ Crash واقعی اپلیکیشن بازتولید نشده؛ نبود Crash نیز تأیید نمی‌شود.

## دستگاه و انتشار

در محیط فعلی `adb` موجود نیست و هیچ Emulator یا دستگاهی برای اجرای محلی وصل نیست. نصب تصویر Android 36 به‌دلیل کمتر از ۱ گیگابایت فضای آزاد متوقف شد؛ فضای موقت دانلود پاک شد. Compose UI، TalkBack، نصب روی دستگاه، ماتریس API و SSH/SFTP واقعی همچنان بررسی نشده‌اند. AGP 8.13.2 دربارهٔ compileSdk 37 هشدار سازگاری می‌دهد، هرچند Build موفق است.

وضعیت انتشار: **NOT VERIFIED**. APK موجود Debug و غیر Production است.

## مجوزها و متن‌باز بودن

کد خود Terminal SSH تحت Apache License 2.0 است؛ متن کامل آن در `LICENSE` قرار دارد. این مجوز به‌تنهایی مجوز اجزای ثالث را جایگزین نمی‌کند. فهرست و ارجاع مجوزهای وابستگی‌های اصلی در `NOTICE.md` آمده است: JSch 2.28.6 (Revised BSD و اعلان‌های اجزای همراه)، termlib 0.2.1 (Apache 2.0)، libvterm (MIT)، Bouncy Castle Provider 1.85 (MIT) و AndroidX/Jetpack Compose (Apache 2.0). پیش از انتشار هر APK/AAB باید گراف دقیق وابستگی‌های resolve‌شده و متن‌های attribution همان Artifact دوباره بررسی شوند.

کلیپ تبلیغاتی از بازسازی وفادار به سورس و داده‌های ساختگی استفاده می‌کند؛ تصاویر آن اسکرین‌شات زنده از APK نیستند. این موضوع و ارجاع مجوزها در `NOTICE.md` و فایل‌های تحویل ویدیو ثبت شده است.

## خروجی‌ها و وضعیت انتشار

- شاخهٔ فعلی `main` است؛ آخرین commit پوش‌شده هنگام ثبت checkpoint `7727c4f` است. اصلاح‌های harness پس از آن در workspace محلی‌اند، کامپایل شده‌اند و برای تأیید runtime به اجرای CI بعدی نیاز دارند.
- تغییرات مطابق درخواست کاربر مستقیماً به `main` پوش شده‌اند؛ لینک شاخه: https://github.com/ashkansuridr-bit/terminal/tree/main . PR جدیدی ایجاد نشده است.
- آخرین CI کامل: https://github.com/ashkansuridr-bit/terminal/actions/runs/38448985782 (Verify موفق، ۱۴ شکست UI در API26/36، API37 boot timeout).
- اجرای CI برای `82b2861` (نتیجه باید دوباره بررسی شود): https://github.com/ashkansuridr-bit/terminal/actions/runs/38395092348
- APK تولیدی/امضاشده: تأیید نشده؛ خروجی Debug با گواهی Debug، نسخهٔ Production محسوب نمی‌شود.
- کلیپ معرفی فارسی: `TerminalSSH-Promo-fa.mp4` و `TerminalSSH-Promo-fa-no-bgm.mp4` در ریشه؛ جزئیات hash و محدودیت شناسایی موسیقی در `docs/terminal-control-studio/VIDEO_DELIVERY.md` و بازبینی مستقل در `VIDEO_REVIEW.md`.
- Checkpoint: `docs/terminal-control-studio/LOOP_CHECKPOINT.md`

## به‌روزرسانی تحویل ریشهٔ مخزن (2026-10-13)

README ریشه با حذف ادعاهای تأییدنشده و درج وضعیت واقعی ساخت، تست، محدودیت‌های ابزارگذاری، روش ساخت و مجوزهای متن‌باز بازنویسی شد. APK نسخهٔ `0.7.2 MarketDebug` با نام `TerminalSSH-0.7.2-MarketDebug-TEST.apk` در ریشه قرار گرفت؛ فایل با کلید Debug امضا شده و فقط برای آزمایش است. SHA-256: `841b97d655f303d6f8319f3b3f2795966a57e99967c70e87f871808540500a5c`؛ جزئیات در `ANDROID_TEST_BUILD.md` و `APK_VERIFICATION.md` است.

اجرای CI مربوط به سورس APK در run `38403567011` اکنون قدیمی است و با آخرین نتیجهٔ کامل run `38439434500` جایگزین شده: Verify موفق، ۲۴ شکست Instrumentation در API26/36، و timeout بوت Emulator برای هر دو job API37. پس رفتار UI و آماده‌بودن انتشار تأیید نشده‌اند. وضعیت TCS همچنان صفر Feature کامل‌شده از ۱۱ است.
