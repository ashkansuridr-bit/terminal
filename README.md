# Terminal SSH for Android

[![Android CI](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/actions/workflows/android-release.yml/badge.svg)](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/actions/workflows/android-release.yml)
[![Latest release](https://img.shields.io/github/v/release/mohamad1313m13-cyber/terminal-ssh-android?include_prereleases&sort=semver)](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases)
[![License](https://img.shields.io/github/license/mohamad1313m13-cyber/terminal-ssh-android)](LICENSE)

کلاینت SSH امن، متن‌باز و فارسی‌محور برای Android با ترمینال واقعی، چند سشن هم‌زمان و نگهداری رمزنگاری‌شدهٔ اطلاعات اتصال.

A secure, open-source, Persian-first SSH client for Android with a real terminal, concurrent sessions, and encrypted local credential storage.

**Current test release: 0.4.1 · Minimum Android: 8.0 (API 26)**

## Download / دانلود

برای نصب و آزمایش روی اکثر گوشی‌ها، نسخهٔ یکپارچهٔ بازار را دریافت کنید:

### [Download Terminal SSH 0.4.1 — Universal APK](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases/download/v0.4.1-test3/TerminalSSH-v0.4.1-test3-market-universal-debug.apk)

| Build | مناسب برای | Download |
| --- | --- | --- |
| Market Universal | پیشنهاد‌شده برای تست روی همهٔ معماری‌ها؛ بدون Google Play Services | [Download APK](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases/download/v0.4.1-test3/TerminalSSH-v0.4.1-test3-market-universal-debug.apk) |
| Market ARM64 | بیشتر گوشی‌های جدید؛ فایل کوچک‌تر | [Download APK](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases/download/v0.4.1-test3/app-market-arm64-v8a-debug.apk) |
| Market ARMv7 | گوشی‌های قدیمی ۳۲ بیتی؛ فایل کوچک‌تر | [Download APK](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases/download/v0.4.1-test3/app-market-armeabi-v7a-debug.apk) |
| Google Play Universal | نسخهٔ دارای مرز اختیاری Google Sign-In | [Download APK](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases/download/v0.4.1-test3/app-gplay-universal-debug.apk) |

[مشاهدهٔ همهٔ فایل‌های نسخهٔ آزمایشی ۳](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases/tag/v0.4.1-test3) · [وضعیت ساخت GitHub Actions](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/actions/workflows/android-release.yml)

> این فایل‌ها build آزمایشی و امضاشده با debug key هستند و برای تست مستقیم روی گوشی مناسب‌اند. نسخهٔ رسمی بازار باید با keystore ناشر امضا شود.

## Highlights / قابلیت‌ها

- اتصال SSH با رمز عبور یا کلید خصوصی و passphrase
- ترمینال واقعی `xterm-256color` مبتنی بر termlib/libvterm
- چند سشن هم‌زمان به‌شکل tab، همراه Foreground Service و اعلان سشن‌های فعال
- reconnect محدود و کنترل‌شده، keepalive و مدیریت چرخهٔ عمر اتصال
- مدیریت سرورها با نام، گروه، برچسب، علاقه‌مندی و جست‌وجو
- بررسی اجباری Host Key با TOFU، اثر انگشت SHA-256 و جلوگیری از پذیرش کلید تغییرکرده
- Snippetهای رمزنگاری‌شده و تأیید ایمنی پیش از paste چندخطی
- کلیدهای کاربردی ترمینال: Esc، Tab، Ctrl، Alt، جهت‌ها، Home، End، PgUp و PgDn
- شش پالت ترمینال، اندازهٔ فونت قابل تنظیم و تایپوگرافی Vazirmatn
- رابط فارسی RTL و رابط انگلیسی، با محافظت bidi برای نسخه، host و port
- flavor مستقل `market` بدون کد Google و flavor اختیاری `gplay`
- APK جداگانه برای `arm64-v8a`، `armeabi-v7a`، `x86_64` و Universal

## Security / امنیت

- Host-key verification همیشه فعال است؛ پروژه از `StrictHostKeyChecking=no` استفاده نمی‌کند.
- رمزها، کلیدها و snippetها با AndroidKeyStore و AES-GCM ذخیره می‌شوند.
- ورودی‌های حساس تا حد امکان از حافظه پاک می‌شوند و خواندن کلید خصوصی محدودیت اندازه دارد.
- backup سیستم و cleartext traffic غیرفعال‌اند؛ صفحهٔ اپ با `FLAG_SECURE` محافظت می‌شود.
- نسخهٔ بازار هیچ کلاس Google Credential یا Google Play Services در APK ندارد.
- Source audit، market gate و loop gate روی CI اجرا می‌شوند.

## Architecture

```text
Compose UI
   │
AppViewModel ── encrypted stores / settings
   │
SessionRegistry ── multiple SshSession instances
   │
JschSshClient ── JSch transport ── SSH server
```

جزئیات بیشتر در [Architecture](docs/ARCHITECTURE.md)، [Design principles](docs/DESIGN_PRINCIPLES.md) و [Current status](docs/STATUS.md) آمده است.

## Build from source / ساخت از سورس

پیش‌نیازها: JDK 17، Android SDK 36 و Gradle Wrapper همراه پروژه.

```sh
# Market build — no Google dependencies
./gradlew testMarketDebugUnitTest lintMarketDebug assembleMarketDebug

# Google Play build
./gradlew testGplayDebugUnitTest lintGplayDebug assembleGplayDebug

# Static release gates
python3 scripts/source_audit.py
python3 scripts/market_release_gate.py
python3 scripts/loop2_gate.py
```

خروجی APKها در مسیرهای زیر ساخته می‌شود:

```text
app/build/outputs/apk/market/debug/
app/build/outputs/apk/gplay/debug/
```

برای Google Sign-In مقدار `GOOGLE_WEB_CLIENT_ID` را هنگام build تنظیم کنید. اتصال SSH برای کارکردن به حساب Google نیاز ندارد.

## Production signing / امضای انتشار

اطلاعات keystore نباید داخل repository قرار بگیرد. راهنمای کامل متغیرهای محیطی و GitHub Secrets در [store/RELEASE_SIGNING.md](store/RELEASE_SIGNING.md) موجود است. بدون signing secrets، pipeline فقط APK آزمایشی قابل‌نصب منتشر می‌کند و آن را نسخهٔ رسمی بازار معرفی نمی‌کند.

## Privacy

اطلاعات میزبان و اسرار اتصال به‌صورت محلی و رمزنگاری‌شده نگهداری می‌شوند. سیاست‌های کامل فارسی و انگلیسی:

- [سیاست حریم خصوصی فارسی](store/PRIVACY_POLICY_FA.md)
- [English privacy policy](store/PRIVACY_POLICY_EN.md)

## License

Licensed under the [Apache License 2.0](LICENSE). Third-party notices are listed in [NOTICE.md](NOTICE.md).
