# معماری

```
TerminalApp (Application)            ← ظرف اصلی؛ عمرش برابر عمر پروسه است
├── AndroidKeyStoreVault             ← رمزها/کلیدها به‌صورت ciphertext
├── KnownHostsStore                  ← کلیدهای عمومی سرورهای مورد اعتماد
├── HostStore                        ← متادیتای غیرمحرمانه (JSON)
├── Settings
├── JschSshClient                    ← آداپتور JSch؛ فقط از ترد پس‌زمینه
└── SessionRegistry                  ← همهٔ سشن‌های باز (تب‌ها)
    └── SshSession × n
        ├── io: ExecutorService      ← تک‌ترد؛ همهٔ عملیات سوکت
        ├── reader: Thread           ← خواندن از SSH
        ├── main: Handler            ← نوشتن روی emulator
        └── emulator: TerminalEmulator

MainActivity → AppViewModel → RootScreen → {Hosts, Terminal, Keys, Settings}
SshForegroundService                 ← وقتی سشن زنده هست
```

## قرارداد ترد — مهم‌ترین بخش

نسخهٔ ۰.۱.۰ دقیقاً به‌خاطر نقض همین قرارداد کرش می‌کرد:

| کار | ترد مجاز |
|---|---|
| connect / write / setPtySize / close | فقط `SshSession.io` |
| `emulator.writeInput` / `clearScreen` | فقط ترد اصلی (`main.post`) |
| خواندن از `shell.input` | فقط ترد `reader` |
| تغییر `_state` | هر تردی (StateFlow امن است) |

هر کد جدیدی که به SSH دست می‌زند باید از این جدول پیروی کند. اگر جایی
`NetworkOnMainThreadException` دیدی، یعنی این قرارداد شکسته شده.

## چرا Room و DI نداریم

کل گراف شش شیء است. Room نیاز به KSP و codegen دارد که در build مارکت
یک نقطهٔ شکست اضافه است. متادیتا JSON ساده در SharedPreferences ذخیره می‌شود.
اگر تعداد سرورها به هزاران رسید، مهاجرت به Room ساده است چون همه‌چیز پشت
`HostStore` پنهان است.

## سشن چطور زنده می‌ماند

`SessionRegistry` در Application است، نه Activity. چرخاندن صفحه یا رفتن به
پس‌زمینه سشن را نمی‌کشد. `SshForegroundService` وقتی حداقل یک سشن زنده است
اجرا می‌شود و با بسته‌شدن آخری متوقف می‌شود. `MainActivity.onDestroy` فقط
وقتی `isFinishing` باشد سشن‌ها را می‌بندد.
