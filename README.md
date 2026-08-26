# Terminal SSH for Android

[![Android CI](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/actions/workflows/android-release.yml/badge.svg)](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/actions/workflows/android-release.yml)
[![Latest release](https://img.shields.io/github/v/release/mohamad1313m13-cyber/terminal-ssh-android?include_prereleases&sort=semver)](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/mohamad1313m13-cyber/terminal-ssh-android/releases)
[![APK size](https://img.shields.io/badge/APK-6.4%20MB-blue)](releases/)
[![License](https://img.shields.io/github/license/mohamad1313m13-cyber/terminal-ssh-android)](LICENSE)

**یک کلاینت SSH که واقعاً فارسی است — نه یک رابط انگلیسی که ترجمه شده.**

A genuinely Persian-first SSH client for Android. Real terminal, concurrent sessions,
SFTP, encrypted local vault, and no account required — ever.

> **۶.۴ مگابایت.** رقبا ده‌ها مگابایت‌اند. بدون حساب کاربری، بدون تبلیغ، بدون Google Play Services.

---

## چرا این و نه Termius؟ / Why this over Termius?

| | Terminal SSH | Termius | JuiceSSH |
|---|---|---|---|
| فارسی و RTL واقعی | ✅ از پایه | ❌ | ❌ |
| بدون اجبار حساب کاربری | ✅ | ❌ اجباری | ✅ |
| بدون Google Play Services | ✅ نسخهٔ `market` | ❌ | ❌ |
| SFTP | ✅ | ✅ پولی | ✅ پولی |
| ساخت کلید داخل اپ | ✅ | ✅ | ❌ |
| نصب عامل کدنویسی روی سرور | ✅ | ❌ | ❌ |
| حجم APK | **۶.۴ MB** | ~۸۰ MB | ~۲۰ MB |
| متن‌باز | ✅ Apache 2.0 | ❌ | ❌ |

صادقانه: چیزهایی که **هنوز نداریم** — Mosh، port forwarding، jump host، همگام‌سازی ابری،
و split view. در [نقشهٔ راه](docs/ROADMAP.md) فهرست شده‌اند.

---

## دانلود / Download

### نسخهٔ آزمایشی — نصب مستقیم

| Build | مناسب برای | حجم | Download |
| --- | --- | --- | --- |
| **arm64-v8a** | اکثر گوشی‌های ۲۰۱۷ به بعد | ۶.۴ MB | [دانلود](releases/TerminalSSH-0.5.1-preview-arm64-v8a.apk) |
| **armeabi-v7a** | گوشی‌های قدیمی‌تر ۳۲ بیتی | ۵.۶ MB | [دانلود](releases/TerminalSSH-0.5.1-preview-armeabi-v7a.apk) |

بعد از دانلود، درستی فایل را با [`releases/SHA256SUMS.txt`](releases/SHA256SUMS.txt) بررسی کنید:

```sh
sha256sum -c SHA256SUMS.txt --ignore-missing
```

> **این بیلد با کلید آزمایشی امضا شده و شناسهٔ جداگانه‌ای دارد**
> (`app.terminalssh.secure.preview`). یعنی کنار نسخهٔ رسمی نصب می‌شود و هرگز جلوی
> به‌روزرسانی به نسخهٔ امضاشدهٔ بازار را نمی‌گیرد. نسخهٔ رسمی باید با keystore ناشر
> امضا شود.

---

## قابلیت‌ها / Features

### اتصال
- SSH با رمز عبور یا کلید خصوصی و passphrase
- بررسی اجباری Host Key با TOFU و اثر انگشت SHA-256؛ کلید تغییرکرده اتصال را **متوقف** می‌کند
- چند سشن هم‌زمان به‌شکل tab، با Foreground Service
- اتصال مجدد خودکار با backoff نمایی و jitter — چند تب بعد از قطعی Wi-Fi هم‌زمان تلاش نمی‌کنند
- بودجهٔ تلاش مجدد برای هر سرور جداگانه
- خطاها به فارسی و انگلیسی توضیح می‌دهند چه چیزی را باید عوض کنی، نه متن خام JSch

### ترمینال
- ترمینال واقعی `xterm-256color` مبتنی بر termlib/libvterm
- نوار کلید ویژه با **بازخورد لمسی**؛ روی نمایشگر ≥۶۰۰dp دو ردیفه می‌شود و اسکرول حذف می‌شود
- Esc، Tab، Ctrl، Alt (با latch)، جهت‌ها، Home، End، PgUp، PgDn
- **حالت گفت‌وگو**: ورودی چندخطی که Enter در آن خط جدید می‌سازد، برای کار با عامل‌های کدنویسی
- ۱۱ پوستهٔ ترمینال شامل Dracula، Nord، Gruvbox، Catppuccin و Tokyo Night

### فایل‌ها (SFTP)
- مرور فایل روی همان اتصال ترمینال — بدون احراز هویت دوم
- صف انتقال با ازسرگیری: قطعی شبکه کارها را دوباره صف می‌کند، خطای دسترسی فوراً متوقف می‌شود
- بدون نیاز به مجوز storage؛ همه‌چیز از طریق file picker سیستم

### امنیت
- کلیدها، رمزها و snippetها در AndroidKeyStore با AES-GCM
- **ساخت کلید SSH داخل اپ** (Ed25519 روی اندروید ۱۳+، ECDSA P-256، RSA-3072)
- قفل بیومتریک با قفل مجدد هنگام خروج از پیش‌زمینه
- پاک‌سازی خودکار کلیپ‌بورد
- **تشخیص فرمان‌های خطرناک** پیش از اجرا
- `FLAG_SECURE`: بدون اسکرین‌شات، بدون پیش‌نمایش در Recents
- backup سیستم و cleartext traffic غیرفعال

### وایب‌کدینگ 🤖
- نصب **Claude Code**، **OpenCode** یا **Aider** روی سرور با چند ضربه
- پیش‌نیازها بر اساس بسته‌مدیر سرور (apt / dnf / pacman / apk)
- **اسکریپت قبل از اجرا کامل نمایش داده می‌شود** — هیچ `curl | bash` کوری
- کلید API در همان Vault، با scope جداگانه برای هر سرور، بدون رفتن به shell history
- tmux برای سشنی که با قطع اتصال نمی‌میرد

### تنظیمات
- **جست‌وجوی فازی** در تنظیمات — «clipbrd» گزینهٔ کلیپ‌بورد را پیدا می‌کند
- حالت ساده / پیشرفته: پیش‌فرض کوتاه می‌ماند، عمق پشت یک کلید
- فشار طولانی روی هر گزینه = بازگردانی به پیش‌فرض
- خروجی و ورودی گرفتن تنظیمات به‌صورت فایل، بدون هیچ راز
- import و export فهرست سرورها به‌صورت `~/.ssh/config` استاندارد

### دسترس‌پذیری و سازگاری
- اندروید ۸.۰ (API 26) به بالا
- چیدمان تطبیقی: از Galaxy Fold بستهٔ ۳۲۰dp تا تبلت ۱۲۸۰dp
- محافظت bidi روی نسخه، آدرس سرور و اثر انگشت

---

## امنیت — قابل بررسی، نه فقط ادعا

| ادعا | چطور بررسی کنی |
|---|---|
| هیچ رازی از دستگاه خارج نمی‌شود | [`scripts/source_audit.py`](scripts/) و کد `AppViewModel` |
| Host key واقعاً بررسی می‌شود | `KnownHostsVerifier` + ۴ تست واحد |
| رازها از حافظه پاک می‌شوند | `ByteArray`/`CharArray` و `fill(0)` در `finally` |
| کلیدهای API به history نمی‌روند | `AgentInstallScript.exportKeyCommand` + تست |
| اسکریپت‌ها تزریق‌پذیر نیستند | ۲۴ تست روی shell quoting |

گزارش آسیب‌پذیری: [SECURITY.md](SECURITY.md) — پاسخ ظرف ۷ روز.

---

## کیفیت

```
۲۰۷ تست واحد (JVM)  ·  ۱۵ تست روی دستگاه  ·  lint بدون خطا  ·  APK ۶.۴ مگابایت
```

```sh
./gradlew testMarketDebugUnitTest lintMarketDebug assembleMarketDebug
```

تست‌های instrumentation به دستگاه یا امولاتور با شتاب سخت‌افزاری نیاز دارند، چون رفتار
AndroidKeyStore از یک تست JVM قابل دسترسی نیست.

---

## معماری

```text
Compose UI ──────────── SettingsCatalog (شِمای اعلانی)
   │
AppViewModel ────────── Vault (AndroidKeyStore + AES-GCM)
   │                    HostStore · SettingsStore
   ├── SessionRegistry ── SshSession × N
   │                        │
   │                    JschSshClient ── SSH server
   └── SftpController ──── SftpClient ──┘
           │
       TransferQueue (خالص، تست‌شده)
```

[Architecture](docs/ARCHITECTURE.md) · [Design principles](docs/DESIGN_PRINCIPLES.md) ·
[Status](docs/STATUS.md) · [Roadmap](docs/ROADMAP.md)

---

## ساخت از سورس

پیش‌نیازها: JDK 17، Android SDK 36.

```sh
# نسخهٔ بازار — بدون هیچ وابستگی Google
./gradlew testMarketDebugUnitTest lintMarketDebug assembleMarketDebug

# نسخهٔ Google Play
./gradlew testGplayDebugUnitTest lintGplayDebug assembleGplayDebug

# بیلد قابل اشتراک‌گذاری (مینیفای‌شده، شناسهٔ جدا)
./gradlew assembleMarketPreview

# گیت‌های استاتیک انتشار
python3 scripts/source_audit.py
python3 scripts/market_release_gate.py
```

| Variant | applicationId | امضا | کاربرد |
|---|---|---|---|
| `marketDebug` | `…secure.debug` | debug | توسعه |
| `marketPreview` | `…secure.preview` | debug | اشتراک‌گذاری برای تست |
| `marketRelease` | `…secure` | keystore ناشر | انتشار در بازار |

برای Google Sign-In مقدار `GOOGLE_WEB_CLIENT_ID` را هنگام build تنظیم کنید.
**اتصال SSH هرگز به حساب Google نیاز ندارد.**

---

## مشارکت

[CONTRIBUTING.md](CONTRIBUTING.md) قواعد واقعی کد را دارد: رازها `ByteArray` هستند نه
`String`، هر نوشتن در Vault مسیر پاک‌سازی خطا دارد، کار سوکت هرگز روی ترد اصلی نیست، و
هر رشتهٔ کاربر در هر دو زبان وجود دارد.

## Privacy

- [سیاست حریم خصوصی فارسی](store/PRIVACY_POLICY_FA.md)
- [English privacy policy](store/PRIVACY_POLICY_EN.md)

## License

[Apache License 2.0](LICENSE) · [NOTICE.md](NOTICE.md)
