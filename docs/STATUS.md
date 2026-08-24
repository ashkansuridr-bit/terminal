# وضعیت واقعی این نسخه — قبل از انتشار بخوانید

## 0.4.1 verification update

نسخهٔ ۰.۴.۱ روی Android 16 emulator واقعاً compile و اجرا شده است. تست‌های واحد هر دو
flavor، lint، ساخت APKهای market/gplay و پنج تست instrumentation AndroidKeyStore و مسیر
ذخیرهٔ سرور پاس شده‌اند. کرش ذخیرهٔ سرور در ۰.۴.۰ ناشی از IV دستی AES-GCM برطرف شده است.

## آنچه قطعاً انجام شده

- سه باگ کرش نسخهٔ ۰.۱.۰ برطرف شده (شبکه روی ترد اصلی در `setPtySize`، `close()`، و نوشتن روی emulator از ترد پس‌زمینه)
- باگ SO_TIMEOUT پانزده‌ثانیه‌ای برطرف شده
- باگ `getHostKey()` که باعث هشدار جعلی «کلید سرور تغییر کرده» می‌شد برطرف شده
- معماری از تک‌Activity به Application container + ViewModel + Service منتقل شده
- UI کامل با Compose و Material 3 نوشته شده

## آنچه باید خودت انجام دهی — قدم اول

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

انتظار داشته باش دور اول چند خطای کامپایل ببینی. محتمل‌ترین‌ها:

1. **امضای API کتابخانهٔ termlib.** کد از همان امضایی استفاده می‌کند که در نسخهٔ ۰.۱.۰ تو
   کار می‌کرد: `TerminalEmulatorFactory.create(initialRows, initialCols, onKeyboardInput,
   onResize, onClipboardCopy)` و `Terminal(terminalEmulator, keyboardEnabled,
   showSoftKeyboard, onPasteRequest)`. اگر نسخهٔ کتابخانه پارامتر دیگری بخواهد،
   `SshSession.kt` و `TerminalScreen.kt` را تطبیق بده.
2. **نسخهٔ Compose BOM.** روی `2024.09.03` تنظیم شده که با Kotlin 2.3 سازگار است.
   اگر خواستی جدیدتر کنی، فقط عدد BOM را عوض کن.
3. **پوستهٔ ترمینال هنوز به رندر وصل نیست.** انتخاب رنگ در تنظیمات ذخیره می‌شود ولی
   termlib باید رنگ‌ها را بپذیرد؛ این را بعد از build اول وصل کن.

## آنچه هنوز ساخته نشده

SFTP، port forwarding، jump host، snippets، تولید کلید داخل اپ، قفل بیومتریک
(کتابخانه‌اش اضافه شده ولی صفحه‌اش نه)، split view، خروجی رمزنگاری‌شدهٔ پروفایل‌ها.

## نسخهٔ iOS

در این تحویل وجود ندارد. دلیل صادقانه: ساخت اپ iOS به Xcode روی macOS، حساب
Apple Developer، و یک پیاده‌سازی SSH کاملاً جدا (SwiftNIO SSH یا libssh2) نیاز دارد —
هیچ‌کدام از کد اندروید قابل استفادهٔ مجدد نیست. ضمناً بازارهای ایرانی (کافه‌بازار، مایکت)
اپ iOS توزیع نمی‌کنند. پیشنهاد: اول اندروید را به دست هزاران کاربر برسان، بعد iOS.


## Loop 2 / 0.4.0 local handoff

Implemented in the 0.4.0 loop handoff:
- encrypted snippets (metadata outside Vault, command bytes inside Vault);
- safe snippet insert (no implicit newline/Enter);
- secure CharArray → UTF-8 conversion without an extra immutable String in ViewModel paths;
- bounded/wiping private-key file input;
- cleanup of imported key secret if metadata persistence fails;
- dynamic version label;
- Remotion/Instavar launch specs.

Verification performed locally:
- static `scripts/loop2_gate.py`;
- pure JVM compile/run harness for `SecretEncoding` and `SecretIo`.

Still blocked in this environment:
- Android Gradle distribution download (`services.gradle.org` DNS);
- emulator/device instrumentation;
- production signing/store validation;
- GitHub branch creation remains subject to connector write authorization.
