<div align="center">

# Terminal SSH for Android

### یک کلاینت SSH که واقعاً فارسی است

**نه یک رابط انگلیسی که ترجمه شده. از پایه برای فارسی‌زبانان ساخته شده.**

<br>

[![Android CI](https://github.com/ashkansuridr-bit/terminal/actions/workflows/android-release.yml/badge.svg)](https://github.com/ashkansuridr-bit/terminal/actions/workflows/android-release.yml)
[![Latest release](https://img.shields.io/github/v/release/ashkansuridr-bit/terminal?include_prereleases&sort=semver)](https://github.com/ashkansuridr-bit/terminal/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/ashkansuridr-bit/terminal/releases)
[![APK size](https://img.shields.io/badge/APK-6.4%20MB-blue)](releases/)
[![License](https://img.shields.io/github/license/ashkansuridr-bit/terminal)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-207%20unit%20%2B%2015%20instrumentation-brightgreen)](#کیفیت)

<br>

**حدود ۱۷ مگابایت** (APK ریلیز ساخته‌شده، بدون تفکیک ABI). رقبا ده‌ها مگابایت‌اند.
بدون حساب کاربری، بدون تبلیغ، بدون Google Play Services.

<br>

<a href="https://github.com/ashkansuri/terminal-ssh/releases">
<img src="https://img.shields.io/badge/⬇_دانلود_آخرین_نسخه-0.6.1-3DDC84?style=for-the-badge&logo=android" alt="Download">
</a>

</div>

---

## چرا Terminal SSH؟

<div dir="rtl">

| ویژگی | Terminal SSH | Termius | JuiceSSH |
|---|:---:|:---:|:---:|
| **فارسی و RTL واقعی** | ✅ از پایه | ❌ | ❌ |
| **۶ زبان** | ✅ FA/EN/RU/ES/FR/AR | ✅ | ✅ |
| **بدون اجبار حساب کاربری** | ✅ | ❌ اجباری | ✅ |
| **بدون Google Play Services** | ✅ نسخه market | ❌ | ❌ |
| **SFTP با صف انتقال** | ✅ | ✅ پولی | ✅ پولی |
| **SFTP resume واقعی** | ✅ قطعی-ایمن | ❌ | ❌ |
| **SFTP سرعت/ETA/پیشرفت** | ✅ | ✅ | ❌ |
| **SFTP حذف/تغییرنام/پوشه جدید** | ✅ | ✅ | ❌ |
| **SFTP چندانتخابی** | ✅ | ✅ | ❌ |
| **SFTP کپی راه‌دور** | ✅ | ❌ | ❌ |
| **SFTP انتقال به پوشه** | ✅ با picker | ✅ | ❌ |
| **SFTP مجوزها (chmod)** | ✅ | ❌ | ❌ |
| **ساخت کلید SSH داخل اپ** | ✅ Ed25519/ECDSA/RSA | ✅ | ❌ |
| **نصب عامل کدنویسی روی سرور** | ✅ Claude/OpenCode/Aider | ❌ | ❌ |
| **قفل بیومتریک** | ✅ | ✅ | ❌ |
| **تشخیص فرمان خطرناک** | ✅ | ❌ | ❌ |
| **حجم APK** | **۶.۴ MB** | ~۸۰ MB | ~۲۰ MB |
| **متن‌باز** | ✅ Apache 2.0 | ❌ | ❌ |
| **prise در بازار ایران** | ✅ | ❌ | ❌ |

</div>

> **صادقانه:** چیزهایی که **هنوز نداریم** — Mosh، port forwarding، jump host،
> split terminal، و search in terminal output. در [نقشهٔ راه](docs/ROADMAP.md) فهرست شده‌اند.
>
> **آمار SFTP:** ۲۵ از ۵۰ بهبود اعمال شده — resume واقعی، سرعت/ETA، چندانتخابی،
> کپی راه‌دور، chmod UI، entry details، و aggregate progress.

---

## دانلود

### نسخهٔ آزمایشی — نصب مستقیم

فایل‌های نسخهٔ ۰.۶.۱ به [صفحهٔ Releases](https://github.com/ashkansuri/terminal-ssh/releases)
پیوست شده‌اند، نه به مخزن. پوشهٔ `releases/` فقط بایگانی نسخه‌های قدیمی است.

### وضعیت امضا — صادقانه

**تا امروز هیچ نسخهٔ امضاشدهٔ تولیدی منتشر نشده است.** کلید اصلی انتشار (production
keystore) در دسترس نیست، بنابراین:

| فایل | شناسه | امضا |
| --- | --- | --- |
| APK ریلیز | `app.terminalssh.secure` | **بدون امضا** — قابل نصب مستقیم نیست |
| APK دیباگ | `app.terminalssh.secure.debug` | کلید دیباگ اندروید — فقط برای تست |
| AAB بازار | `app.terminalssh.secure` | **بدون امضا** |

انتشار در کافه‌بازار به AAB به‌همراه فایل `.bin` تولیدشده با `bundlesigner` و کلید
اصلی نیاز دارد (v2 روشن، v3 خاموش). تا زمانی که آن کلید در دسترس نباشد، هیچ فایلی
را «آمادهٔ بازار» نمی‌نامیم.

### تأیید صحت فایل

```sh
sha256sum -c SHA256SUMS.txt --ignore-missing
```

---

## قابلیت‌ها

<div dir="rtl">

### 🔌 اتصال
- SSH با رمز عبور یا کلید خصوصی و passphrase
- **بررسی اجباری Host Key** با TOFU و اثر انگشت SHA-256 — کلید تغییرکرده اتصال را **متوقف** می‌کند
- **چند سشن هم‌زمان** به‌شکل tab، با Foreground Service
- **اتصال مجدد خودکار** با backoff نمایی و jitter
- بودجهٔ تلاش مجدد برای هر سرور **جداگانه**
- خطاها به فارسی و انگلیسی توضیح می‌دهند چه چیزی را باید عوض کنی

### 💻 ترمینال
- ترمینال واقعی `xterm-256color` مبتنی بر termlib/libvterm (ConnectBot)
- **نوار کلید ویژه** با بازخورد لمسی — روی نمایشگر ≥۶۰۰dp دو ردیفه می‌شود
- Esc، Tab، Ctrl، Alt (با latch)، جهت‌ها، Home، End، PgUp، PgDn
- **حالت گفت‌وگو**: ورودی چندخطی برای کار با عامل‌های کدنویسی
- **۱۱ پوستهٔ ترمینال**: Dracula، Nord، Gruvbox، Catppuccin، Tokyo Night و ۶ تای دیگر

### 📁 فایل‌ها (SFTP)
- مرور فایل روی **همان اتصال ترمینال** — بدون احراز هویت دوم
- صف انتقال با **ازسرگیری واقعی**: قطعی شبکه بدون از دست دادن فایل
- **سرعت انتقال، ETA، و پیشرفت تجمعی** — دقیقاً می‌دانی چه زمانی تمام می‌شود
- **حذف، تغییرنام، و ایجاد پوشه** مستقیماً از رابط کاربری
- **چندانتخابی** برای حذف/دانلود دسته‌ای
- **کپی راه‌دور** — فایل بین دو سرور بدون دانلود به گوشی
- **انتقال به پوشه** با picker دایرکتوری
- **نمایش مجوزها (chmod)** و جزئیات entry (سایز، تاریخ، symlink)
- **بدون نیاز به مجوز storage** — همه‌چیز از طریق file picker سیستم

### 🔐 امنیت
- کلیدها، رمزها و snippetها در **AndroidKeyStore** با AES-256-GCM
- **ساخت کلید SSH داخل اپ** — Ed25519 (اندروید ۱۳+)، ECDSA P-256، RSA-3072
- **قفل بیومتریک** با قفل مجدد هنگام خروج از پیش‌زمینه
- پاک‌سازی خودکار کلیپ‌بورد
- **تشخیص فرمان‌های خطرناک** پیش از اجرا (`rm -rf /`، `dd`، `mkfs`، ...)
- `FLAG_SECURE`: بدون اسکرین‌شات، بدون پیش‌نمایش در Recents
- غیرفعال‌سازی backup سیستم و cleartext traffic

### 🤖 وایب‌کدینگ
- نصب **Claude Code**، **OpenCode** یا **Aider** روی سرور با چند ضربه
- پیش‌نیازها بر اساس بسته‌مدیر سرور (apt / dnf / pacman / apk)
- **اسکریپت قبل از اجرا کامل نمایش داده می‌شود** — هیچ `curl | bash` کوری
- کلید API در همان Vault، با scope جداگانه برای هر سرور
- tmux برای سشنی که با قطع اتصال نمی‌میرد

### ⚙️ تنظیمات
- **جست‌وجوی فازی** در تنظیمات — «clipbrd» گزینهٔ کلیپ‌بورد را پیدا می‌کند
- خروجی و ورودی گرفتن تنظیمات به‌صورت فایل JSON
- import و export فهرست سرورها به‌صورت `~/.ssh/config` استاندارد

### 🌍 زبان‌ها
- **فارسی** (پیش‌فرض) — RTL از پایه، نه ترجمه
- **English** — کامل و بومی
- **Русский** — کامل
- **Español** — کامل
- **Français** — کامل
- **العربية** — کامل با RTL

### 📱 سازگاری
- **اندروید ۸.۰ (API 26) به بالا**
- چیدمان تطبیقی: از Galaxy Fold بستهٔ ۳۲۰dp تا تبلت ۱۲۸۰dp
- محافظت bidi روی نسخه، آدرس سرور و اثر انگشت
- شورتکات‌های لانچر برای ۴ سرور اخیر

</div>

---

## امنیت — قابل بررسی، نه فقط ادعا

<div dir="rtl">

| ادعا | چطور بررسی کنی |
|---|---|
| هیچ رازی از دستگاه خارج نمی‌شود | [`scripts/source_audit.py`](scripts/) و کد `AppViewModel` |
| Host key واقعاً بررسی می‌شود | `KnownHostsVerifier` + ۴ تست واحد |
| رازها از حافظه پاک می‌شوند | `ByteArray`/`CharArray` و `fill(0)` در `finally` |
| کلیدهای API به history نمی‌روند | `AgentInstallScript.exportKeyCommand` + تست |
| اسکریپت‌ها تزریق‌پذیر نیستند | ۲۴ تست روی shell quoting |

</div>

گزارش آسیب‌پذیری: [SECURITY.md](SECURITY.md) — پاسخ ظرف ۷ روز.

---

## معماری

```
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

### فناوری‌ها

| لایه | فناوری |
|---|---|
| UI | Jetpack Compose + Material 3 |
| SSH | JSch 2.28.6 |
| ترمینال | termlib/libvterm (ConnectBot) |
| امنیت | AndroidKeyStore + AES-256-GCM |
| زبان | Kotlin 2.3.21 |
| بیلد | AGP 8.13.2 + Gradle 8.13 |
| فونت | Vazirmatn (فارسی) |

[Architecture](docs/ARCHITECTURE.md) · [Design principles](docs/DESIGN_PRINCIPLES.md) ·
[Status](docs/STATUS.md) · [Roadmap](docs/ROADMAP.md)

---

## کیفیت

<div dir="rtl">

```
۲۰۷ تست واحد (JVM)  ·  ۱۵ تست روی دستگاه  ·  lint بدون خطا  ·  APK ۶.۴ مگابایت
```

</div>

```sh
# اجراي تست‌ها و بیلد
./gradlew testMarketDebugUnitTest lintMarketDebug assembleMarketDebug

# بیلد Google Play
./gradlew testGplayDebugUnitTest lintGplayDebug assembleGplayDebug

# بیلد قابل اشتراک‌گذاری (مینیفای‌شده)
./gradlew assembleMarketPreview

# گیت‌های استاتیک انتشار
python3 scripts/source_audit.py
python3 scripts/market_release_gate.py
```

---

## ساخت از سورس

پیش‌نیازها: **JDK 17**، **Android SDK 36**

```sh
git clone https://github.com/ashkansuridr-bit/terminal.git
cd terminal
```

| Variant | applicationId | امضا | کاربرد |
|---|---|---|---|
| `marketDebug` | `…secure.debug` | debug | توسعه |
| `marketPreview` | `…secure.preview` | debug | اشتراک‌گذاری برای تست |
| `marketRelease` | `…secure` | keystore ناشر | انتشار در بازار |

---

## نقشهٔ راه

<div dir="rtl">

### ✅ انجام شده
- [x] پایدارسازی و رفع خطاهای کامپایل
- [x] تست روی دستگاه واقعی
- [x] SFTP با مرورگر فایل
- [x] **SFTP resume واقعی** — قطعی‌ایمن با ازسرگیری خودکار
- [x] **SFTP سرعت/ETA/پیشرفت تجمعی**
- [x] **SFTP حذف/تغییرنام/پوشه جدید** از رابط کاربری
- [x] **SFTP چندانتخابی** برای عملیات دسته‌ای
- [x] **SFTP کپی راه‌دور** بین دو سرور
- [x] **SFTP انتقال به پوشه** با picker دایرکتوری
- [x] **SFTP chmod UI** — نمایش و ویرایش مجوزها
- [x] **۶ زبان**: فارسی، انگلیسی، روسی، اسپانیایی، فرانسوی، عربی
- [x] Snippets رمزنگاری‌شده
- [x] تولید کلید Ed25519/ECDSA/RSA داخل اپ
- [x] قفل بیومتریک
- [x] نصب عامل کدنویسی (Claude Code / OpenCode / Aider)
- [x] Import/Export از `~/.ssh/config`

### 🔜 در حال توسعه
- [ ] Port forwarding (local، remote، SOCKS)
- [ ] Jump host / bastion
- [ ] Split terminal (افقی و عمودی)
- [ ] جستجو در خروجی ترمینال
- [ ] همگام‌سازی ابری رمزنگاری‌شده

### 🎯 اهداف بلندمدت
- [ ] Mosh
- [ ] انتشار در کافه‌بازار، مایکت، و F-Droid
- [ ] پایداری زیر بار واقعی
- [ ] ترجمهٔ کامل و بازبینی متن‌ها

### ❌ عمداً انجام نمی‌شود
- **Google Sign-In / sync ابری:** روی گوشی‌های داخل ایران Play Services قابل اتکا نیست
- **تلمتری:** هیچ داده‌ای از دستگاه خارج نمی‌شود جز خود اتصال SSH

</div>

---

## مشارکت

[CONTRIBUTING.md](CONTRIBUTING.md) قواعد واقعی کد را دارد:

<div dir="rtl">

- رازها `ByteArray` هستند نه `String`
- هر نوشتن در Vault مسیر پاک‌سازی خطا دارد
- کار سوکت هرگز روی ترد اصلی نیست
- هر رشتهٔ کاربر در هر دو زبان (فارسی و انگلیسی) وجود دارد

</div>

---

## حریم خصوصی

- [سیاست حریم خصوصی فارسی](store/PRIVACY_POLICY_FA.md)
- [English privacy policy](store/PRIVACY_POLICY_EN.md)

<div dir="rtl">

- هیچ داده‌ای از دستگاه خارج نمی‌شود
- backup سیستم غیرفعال است
- cleartext traffic غیرفعال است
- لاگین گوگل اختیاری است (فلاور gplay)

</div>

---

## مجوز

[Apache License 2.0](LICENSE) · [NOTICE.md](NOTICE.md)

Copyright 2026 Ashkan Suri

---

<div align="center">

**ساخته شده با ❤️ برای فارسی‌زبانان**

</div>
