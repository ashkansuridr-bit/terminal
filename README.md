<div align="center">

# Terminal SSH for Android

### یک کلاینت SSH که واقعاً فارسی است

**نه یک رابط انگلیسی که ترجمه شده. از پایه برای فارسی‌زبانان ساخته شده.**

<br>

[![Android CI](https://github.com/ashkansuridr-bit/terminal/actions/workflows/android-release.yml/badge.svg)](https://github.com/ashkansuridr-bit/terminal/actions/workflows/android-release.yml)
[![Latest release](https://img.shields.io/github/v/release/ashkansuridr-bit/terminal?include_prereleases&sort=semver)](https://github.com/ashkansuridr-bit/terminal/releases)
[![Android](https://img.shields.io/badge/Android-8.0%20%E2%80%93%2017%20%28API%2026%E2%80%9337%29-3DDC84?logo=android&logoColor=white)](https://github.com/ashkansuridr-bit/terminal/releases)
[![License](https://img.shields.io/github/license/ashkansuridr-bit/terminal)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-930%20unit%20per%20flavor-brightgreen)](#کیفیت)

<br>

**حدود ۱۷ مگابایت** (APK ریلیز ساخته‌شده، بدون تفکیک ABI). رقبا ده‌ها مگابایت‌اند.
بدون حساب کاربری، بدون تبلیغ، بدون Google Play Services.

<br>

> **وضعیت نسخهٔ ۰.۷.۲ — کاندیدای انتشار، ادغام دو شاخهٔ کاری:**
> این نسخه دو شاخهٔ مستقلِ منشعب از ۰.۷.۱ را روی `main` ادغام می‌کند:
> **موتور ۵۰ قابلیت** (`feature/terminal-50-tools`) و
> **دور رفع اشکال پایداری/ابزارگیری ۵۵ موردی** (`fix/0.7.2-instrumentation-reliability`).
> ۴۷ قابلیت از ۵۰ پیاده‌سازی و با تست واحد JVM سبز شده‌اند؛ ۲ مورد همچنان
> `BLOCKED` (نبود transport) و ۱ مورد به‌تعویق‌افتاده است.
> بخش بزرگی از این قابلیت‌ها در سطح **موتور منطقی خالص + تست JVM** آماده‌اند و
> **رندر روی دستگاه واقعی تأیید نشده است**. تست واقعی SSH/SFTP و **امضای تولیدی**
> هم انجام نشده؛ هیچ فایلی «آمادهٔ بازار» نیست. جزئیات دقیق:
> [`docs/features/FEATURE_STATUS.json`](docs/features/FEATURE_STATUS.json) ·
> [`docs/BUGFIX_STATUS.json`](docs/BUGFIX_STATUS.json).

<a href="https://github.com/ashkansuridr-bit/terminal/releases">
<img src="https://img.shields.io/badge/⬇_دانلود_آخرین_نسخه-0.7.2_TEST_SIGNED-3DDC84?style=for-the-badge&logo=android" alt="Download">
</a>

</div>

---

## جدید در ۰.۷.۲

<div dir="rtl">

### 🖥️ ابزارهای سرور (تمام‌صفحهٔ جدید)
- **داشبورد منابع سرور** — خواندن قابل‌حمل از `/proc` و `df` (بارگذاری، حافظه، دیسک)
- **نمایشگر لاگ زنده** — بافر محدود، قابل‌توقف، با فیلتر و پاک‌سازی CRLF
- **مدیریت پروسه** — فهرست `ps` + ارسال سیگنال با فهرست سفید و تأیید
- **مدیریت systemd** — فهرست unit‌ها، اکشن‌های نقل‌قول‌شده، مشاهدهٔ `journalctl`
- **داشبورد Docker** — کانتینرها، `stats`/`inspect`، لاگ محدود، اکشن‌های تأییدشده
- **Git روی سرور** — تجزیهٔ `porcelain=v2` (شاخه، ahead/behind، تعارض)
- **پورت‌ها و سرویس‌ها** — `ss` و `netstat`
- **Runbook ایمن** — پیش‌نمایش dry-run، دروازهٔ تأیید، توقف روی خطا، ازسرگیری

### 📁 فایل‌ها (SFTP)
- **حل تعارض با «اعمال روی همه»** — پرسش دوباره در انتقال پوشه متوقف می‌شود
- **پیش‌نمایش انتقال پوشه** — تعداد فایل/پوشه/بایت، تعارض، فایل‌های ناخوانا، symlink
- **کنترل دسته‌ای** — Pause/Resume/Retry/Cancel برای کل صف
- **اندازهٔ پوشهٔ راه‌دور** — محدود به تعداد/زمان، قابل‌لغو، با دلیل توقف
- **جست‌وجوی فایل راه‌دور** — با محدودیت عمق و تعداد
- **پیش‌نمایش فایل** — متن برای فایلی متنی، hex/ascii برای دودویی
- **یکپارچگی SHA-256 صادقانه** — `verified` فقط با تطبیق واقعی، وگرنه `unverified`
  (هرگز موفقیتِ خاموش)، هم در ردیف زنده و هم در تاریخچهٔ انتقال
- **سیاست صف** — اولویت + FIFO + حجم، سقف همزمانی، backoff نمایی، خطای موقت/دائمی

### 💻 ترمینال
- **جست‌وجو در scrollback** با بعدی/قبلی، wrap و شمارش تطبیق (سازگار با یونی‌کد)
- **نشانک** روی خطوط خروجی با مدیریت trim
- **انتخاب/کپی/ذخیرهٔ خروجی** با سقف بایت و هشدار خروجی مشکوک به راز
- **تشخیص لینک** با سیاست امن/تأیید/مسدود برای اسکیم‌ها
- **تاریخچهٔ اختیاری فرمان محلی** — خاموش به‌صورت پیش‌فرض، محدود، حذف خطوط مشکوک
- **پخش ورودی به چند ترمینال** (broadcast) پشت دروازهٔ تأیید
- **نوار کلید و نشانگر قابل‌تنظیم** (شکل + چشمک)

### 🔌 اتصال و سرورها
- **اتصال سریع بدون ذخیره** — `ssh://user@host:port` با پشتیبانی IPv6
- **برچسب رنگی + فیلتر** (tag/env/علاقه‌مندی/جست‌وجو)
- **انتخابگر آیکون سرور** به‌صورت آفلاین
- **وضعیت اتصال تجمیعی** در لیست و نوار تب
- **کپی سریع** hostname/IP/username/port بدون خواندن راز
- **تنظیمات پیشرفتهٔ هر سرور** — timeout، keep-alive، compression، reconnect
- **عیب‌یابی اتصال** — مدل فازی DNS/TCP/KEX/host-key/auth/shell
- **known_hosts** — import/export و اثر انگشت SHA-256
- **تحلیل `~/.ssh/config`** — گزارش Include/IdentityFile/ProxyJump که اعمال نمی‌شوند

### 🔐 امنیت
- **پشتیبان رمزنگاری‌شدهٔ محلی** — PBKDF2-HMAC-SHA256 (۲۱۰هزار) + AES-256-GCM
  با header به‌عنوان AAD (نمی‌توان هزینهٔ KDF را حذف کرد)

### 🛠️ رفع اشکال (دور پایداری)
- مهلت واقعی برای `exec` روی کانال SSH و رد کد خروجی غیرصفر
- مسیرهای Jump پیکربندی‌شدهٔ پشتیبانی‌نشده **fail-closed** (بدون اتصال مستقیم پنهانی)
- عدم نگه‌داری تاریخچهٔ فرمان محلی استفاده‌نشده (حریم خصوصی)
- عملیات بازگشتی/پوشهٔ SFTP **fail-closed**؛ گارد پیمایش `chmod` و پرش symlink
- مدیریت صحیح null در ساخت سند SAF
- هدف‌های لمسی بزرگ‌تر و دسترس‌پذیری صفحه‌خوان

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
| **یکپارچگی SHA-256 صادقانه** | ✅ | ❌ | ❌ |
| **ابزارهای سرور (Docker/systemd/پروسه)** | ✅ | ❌ | ❌ |
| **ساخت کلید SSH داخل اپ** | ✅ Ed25519/ECDSA/RSA | ✅ | ❌ |
| **نصب عامل کدنویسی روی سرور** | ✅ Claude/OpenCode/Aider | ❌ | ❌ |
| **قفل بیومتریک** | ✅ | ✅ | ❌ |
| **تشخیص فرمان خطرناک** | ✅ | ❌ | ❌ |
| **حجم APK** | **~۱۸ MB** | ~۸۰ MB | ~۲۰ MB |
| **متن‌باز** | ✅ Apache 2.0 | ❌ | ❌ |
| **انتشار در بازار ایران** | ✅ | ❌ | ❌ |

</div>

> **صادقانه:** چیزهایی که **هنوز نداریم** — Mosh، split terminal، و
> multi-hop ProxyJump (فقط موتور برنامه‌ریزی خالص و تست‌شده دارند، نه transport).
> این‌ها در [نقشهٔ راه](docs/ROADMAP.md) و `docs/features/FEATURE_STATUS.json` فهرست شده‌اند.

---

## دانلود

### نسخهٔ آزمایشی — نصب مستقیم

**آخرین فایل قابل نصب** (امضای تست، یونیورسال):
[`TerminalSSH-0.7.2-preview-TEST-SIGNED.apk`](releases/TerminalSSH-0.7.2-preview-TEST-SIGNED.apk)
— نسخهٔ بدون امضا (`…market-UNSIGNED.apk`) نصب‌شدنی نیست و فقط برای امضا با کلید ناشر است.
هر دو (و AAB بازار بدون امضا) در پوشهٔ [`releases/`](releases/) هستند؛ هویت و امضای آن‌ها
پیش از ارائه ماشینی بررسی شده است (`scripts/verify_release_artifacts.py`).

فایل‌های نسخه‌های قدیمی‌تر همچنان به‌عنوان بایگانی در `releases/` و صفحهٔ Releases نگهداری می‌شوند.

### وضعیت امضا — صادقانه

**تا امروز هیچ نسخهٔ امضاشدهٔ تولیدی منتشر نشده است.** کلید اصلی انتشار (production
keystore) در دسترس نیست، بنابراین:

| فایل | شناسه | امضا |
| --- | --- | --- |
| APK ریلیز | `app.terminalssh.secure` | **بدون امضا** — قابل نصب مستقیم نیست |
| APK دیباگ/پیش‌نمایش | `app.terminalssh.secure.preview` | کلید دیباگ اندروید — فقط برای تست |
| AAB بازار | `app.terminalssh.secure` | **بدون امضا** |

### تأیید صحت فایل

```sh
cd releases && sha256sum -c SHA256SUMS.txt
```

گزارش‌های تأیید: [`releases/ARTIFACTS.md`](releases/ARTIFACTS.md) ·
[`releases/SHA256SUMS.txt`](releases/SHA256SUMS.txt)

---

## قابلیت‌ها

<div dir="rtl">

### 🔌 اتصال
- SSH با رمز عبور یا کلید خصوصی و passphrase
- **بررسی اجباری Host Key** با TOFU و اثر انگشت SHA-256 — کلید تغییرکرده اتصال را **متوقف** می‌کند
- **چند سشن هم‌زمان** به‌شکل tab، با Foreground Service
- **اتصال سریع بدون ذخیرهٔ پروفایل** و **تنظیمات پیشرفتهٔ هر سرور**
- **اتصال مجدد خودکار** با backoff نمایی و jitter
- **عیب‌یابی اتصال** مرحله‌به‌مرحله و خطاهای فارسی توضیح‌دار

### 💻 ترمینال
- ترمینال واقعی `xterm-256color` مبتنی بر termlib/libvterm (ConnectBot)
- **شکل‌دهی درست متن فارسی/عربی**: چیدمان دوجهتهٔ یونی‌کد (BiDi) و اتصال حروف (اندروید ۱۲+ / API 31+)
- **نوار کلید ویژه** با بازخورد لمسی و پیکربندی سفارشی
- **جست‌وجو در scrollback**، **نشانک**، **تشخیص لینک**، **تاریخچهٔ اختیاری فرمان**
- **پخش ورودی به چند ترمینال**
- **انتخاب/کپی/ذخیرهٔ خروجی** با سقف ایمن
- **حالت گفت‌وگو**: ورودی چندخطی برای کار با عامل‌های کدنویسی
- **۱۱ پوستهٔ ترمینال**: Dracula، Nord، Gruvbox، Catppuccin، Tokyo Night و ۶ تای دیگر

### 📁 فایل‌ها (SFTP)
- مرور فایل روی **همان اتصال ترمینال** — بدون احراز هویت دوم
- صف انتقال با **ازسرگیری واقعی** و **سرعت/ETA/پیشرفت تجمعی**
- **پیش‌نمایش انتقال پوشه** و **کنترل دسته‌ای** (Pause/Resume/Retry/Cancel)
- **حل تعارض با اعمال روی همه**، **جست‌وجوی فایل راه‌دور**، **اندازهٔ پوشهٔ راه‌دور**
- **پیش‌نمایش متن/hex** و **یکپارچگی SHA-256 صادقانه**
- **حذف، تغییرنام، ایجاد پوشه، چندانتخابی، کپی راه‌دور، chmod**
- **بدون نیاز به مجوز storage** — همه‌چیز از طریق file picker سیستم

### 🖥️ ابزارهای سرور
- داشبورد منابع، نمایشگر لاگ زنده، مدیریت پروسه و systemd، Docker، Git، پورت‌ها، و Runbook ایمن
- همهٔ فرمان‌ها **فقط‌خواندنی**اند مگر اکشن‌های صریح، با نقل‌قول و دروازهٔ تأیید

### 🔐 امنیت
- کلیدها، رمزها و snippetها در **AndroidKeyStore** با AES-256-GCM
- **پشتیبان رمزنگاری‌شدهٔ محلی** (PBKDF2 + AES-GCM)
- **ساخت کلید SSH داخل اپ** — Ed25519 (اندروید ۱۳+)، ECDSA P-256، RSA-3072
- **قفل بیومتریک** با قفل مجدد هنگام خروج از پیش‌زمینه
- پاک‌سازی خودکار کلیپ‌بورد و **تشخیص فرمان‌های خطرناک**
- `FLAG_SECURE`: بدون اسکرین‌شات، بدون پیش‌نمایش در Recents
- غیرفعال‌سازی backup سیستم و cleartext traffic

### ⚙️ تنظیمات
- **جست‌وجوی فازی** در تنظیمات
- خروجی/ورودی تنظیمات به‌صورت JSON
- import/export فهرست سرورها به‌صورت `~/.ssh/config`
- **ذخیره/بازیابی فضای کاری**

### 🌍 زبان‌ها
- **فارسی** (پیش‌فرض) — RTL از پایه، نه ترجمه · **English** · **Русский** · **Español** · **Français** · **العربية**

### 📱 سازگاری
- **اندروید ۸.۰ (API 26) تا اندروید ۱۷ (API 37)**؛ `compileSdk` و `targetSdk` هر دو ۳۷
- اندروید ۱۷: مجوز «دستگاه‌های اطراف» (`ACCESS_LOCAL_NETWORK`) فقط هنگام اتصال به آدرس محلی
- چیدمان تطبیقی از ۳۲۰dp تا ۱۲۸۰dp؛ محافظت bidi روی نسخه/آدرس/اثر انگشت
- شورتکات‌های لانچر برای ۴ سرور اخیر

</div>

---

## امنیت — قابل بررسی، نه فقط ادعا

<div dir="rtl">

| ادعا | چطور بررسی کنی |
|---|---|
| هیچ رازی از دستگاه خارج نمی‌شود | [`scripts/source_audit.py`](scripts/) و کد `AppViewModel` |
| Host key واقعاً بررسی می‌شود | `KnownHostsVerifier` + تست واحد |
| رازها از حافظه پاک می‌شوند | `ByteArray`/`CharArray` و `fill(0)` در `finally` |
| فرمان‌های سرور تزریق‌پذیر نیستند | نقل‌قول در همهٔ ابزارهای جدید + تست quoting |
| پشتیبان قابل دست‌کاری نیست | header در AAD؛ تمایز گذرواژهٔ غلط از دست‌کاری |

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
| امنیت | AndroidKeyStore + AES-256-GCM · PBKDF2 |
| زبان | Kotlin 2.3.21 |
| بیلد | AGP 8.13.2 + Gradle 8.13 (API 37 بدون خطا build می‌شود) |
| فونت | Vazirmatn (فارسی) |

[Architecture](docs/ARCHITECTURE.md) · [Design principles](docs/DESIGN_PRINCIPLES.md) ·
[Status](docs/STATUS.md) · [Roadmap](docs/ROADMAP.md)

---

## کیفیت

<div dir="rtl">

```
۹۳۰ تست واحد (JVM) برای flavor بازار  ·  lint بدون خطا  ·  CI روی API 26/36/37
```

موتور هر قابلیت جدید **خالص (pure)** و با تست واحد JVM پوشش داده شده است.
تست‌های روی دستگاه (API 26 و 36 و آزمایشی 37) روی هر PR در GitHub Actions اجرا می‌شوند و همین CI
**مرجع تأیید** است. ایمولاتور محلی روی این ماشین آزمایشی منابع کافی ندارد؛
در گزارش‌ها به‌عنوان **INFRA** ثبت می‌شود، نه شکست برنامه.

</div>

```sh
# اجرای تست‌ها و بیلد
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

پیش‌نیازها: **JDK 17**، **Android SDK 37** (`platforms;android-37.0`)، `build-tools;35.0.0`

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
- [x] پایدارسازی، رفع خطاهای کامپایل، و تست روی دستگاه
- [x] SFTP کامل: resume، سرعت/ETA، حذف/تغییرنام/پوشه، چندانتخابی، کپی راه‌دور، chmod
- [x] **پیش‌نمایش انتقال، حل تعارض، کنترل دسته‌ای، جست‌وجو، اندازهٔ پوشه، پیش‌نمایش hex**
- [x] **یکپارچگی SHA-256 صادقانه**
- [x] **جست‌وجو در scrollback، نشانک، لینک، تاریخچهٔ فرمان، پخش ورودی**
- [x] **اتصال سریع، برچسب/فیلتر، آیکون، وضعیت اتصال، تنظیمات per-host، عیب‌یابی**
- [x] **ابزارهای سرور: منابع، لاگ، پروسه، systemd، Docker، Git، پورت‌ها، Runbook**
- [x] **پشتیبان رمزنگاری‌شدهٔ محلی**
- [x] **پورت‌فورواردینگ با فرم اعتبارسنجی‌شده**
- [x] ۶ زبان، Snippets رمزنگاری‌شده، تولید کلید، قفل بیومتریک، نصب عامل کدنویسی

### 🔜 در حال توسعه (موتور آماده، transport/رندر باقی‌مانده)
- [ ] Split terminal (افقی و عمودی) — `PaneLayout` تست‌شده؛ رندر دو بافر وجود ندارد
- [ ] Jump host / ProxyJump چندمرحله‌ای — `JumpChain` تست‌شده؛ زنجیرهٔ تونل پیاده نشده
- [ ] رندر و تأیید دستگاه واقعی برای همهٔ UIهای جدید

### 🎯 اهداف بلندمدت
- [ ] Mosh
- [ ] انتشار در کافه‌بازار، مایکت، و F-Droid
- [ ] پایداری زیر بار واقعی و ترجمهٔ بازبینی‌شده

### ❌ عمداً انجام نمی‌شود
- **Google Sign-In / sync ابری:** در ایران Play Services قابل اتکا نیست
- **تلمتری:** هیچ داده‌ای از دستگاه خارج نمی‌شود جز خود اتصال SSH

</div>

---

## مشارکت

[CONTRIBUTING.md](CONTRIBUTING.md) قواعد واقعی کد را دارد:

<div dir="rtl">

- رازها `ByteArray` هستند نه `String`
- هر نوشتن در Vault مسیر پاک‌سازی خطا دارد
- کار سوکت هرگز روی ترد اصلی نیست
- هر رشتهٔ کاربر در همهٔ زبان‌ها وجود دارد

</div>

---

## حریم خصوصی

- [سیاست حریم خصوصی فارسی](store/PRIVACY_POLICY_FA.md)
- [English privacy policy](store/PRIVACY_POLICY_EN.md)

---

## مجوز

کد منبع این پروژه تحت [Apache License 2.0](LICENSE) منتشر شده و مشارکت‌های ارسالی نیز مشمول همان مجوز هستند، مگر اینکه برای یک فایل خلاف آن تصریح شده باشد. این مجوز برای کد خود پروژه است؛ وابستگی‌ها مجوزهای جداگانهٔ خود را دارند.

نسخه‌های SSH، رمزنگاری و ترمینال در فایل Build ثبت شده‌اند. نام، نسخه و مجوز کتابخانه‌های ثالث را در [NOTICE.md](NOTICE.md) ببینید و هنگام توزیع APK/AAB، متن‌های مجوز همراه هر وابستگی را نگه دارید.

[Apache License 2.0](LICENSE) · [مجوزهای وابستگی‌های ثالث](NOTICE.md)

فایل‌های ویدیوی معرفی و توضیح مجوز موسیقی/فونت/افکت‌های به‌کاررفته در آن در [گزارش تحویل ویدیو](docs/terminal-control-studio/VIDEO_DELIVERY.md) ثبت شده‌اند. فایل MP4 تحت مجوز Apache سورس برنامه عرضه نمی‌شود.

Copyright 2026 Ashkan Suri

---

<div align="center">

**ساخته شده با ❤️ برای فارسی‌زبانان**

</div>
