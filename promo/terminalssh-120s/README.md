# پروژهٔ سورس کلیپ ۱۲۰ثانیه‌ای Terminal SSH

خروجی اصلی در ریشهٔ مخزن با نام `TerminalSSH-Promo-fa-120s.mp4` و چهار فصل ۳۰ثانیه‌ای در `chapters/` نگه‌داری می‌شوند. ویدیو عمودی ۹:۱۶، 1080×1920، 30fps، H.264 و بدون ترک صوتی است. فصل‌ها ابتدا جداگانه ساخته و با stream-copy متصل شده‌اند؛ تصویر در مرحلهٔ اتصال دوباره فشرده نشده است.

چهار composition مستقل و composition بلند در `src/LongRoot.tsx` تعریف شده‌اند. برای بازسازی آن‌ها از Node.js/npm سازگار با Remotion 4.0.484 استفاده کنید:

```bash
npm ci
npx remotion render src/long-index.ts TerminalPromo30A out/chapter-01.mp4 --codec=h264 --crf=18 --concurrency=2
npx remotion render src/long-index.ts TerminalPromo30B out/chapter-02.mp4 --codec=h264 --crf=18 --concurrency=2
npx remotion render src/long-index.ts TerminalPromo30C out/chapter-03.mp4 --codec=h264 --crf=18 --concurrency=2
npx remotion render src/long-index.ts TerminalPromo30D out/chapter-04.mp4 --codec=h264 --crf=18 --concurrency=2
```

هر خروجی Remotion را برای برداشتن ترک AAC بی‌صدا و حفظ تصویر با stream-copy نرمال کنید، سپس چهار فایل را با concat demuxer و `-c:v copy` به هم متصل کنید. فایل نهایی تحویل‌شده و فصل‌هایش قبلاً از نظر مدت/فریم بررسی شده‌اند؛ برای بازسازی آینده، پس از هر رندر دوباره همهٔ Gateها را اجرا کنید. `STORYBOARD_FA.md` زمان‌بندی و منشأ حرکت‌ها را ثبت می‌کند و `qa/` چهار فریم نماینده را نگه می‌دارد.

تصاویر داخل `public/screens/` بازسازی‌های وفادار به Compose و منابع برنامه‌اند؛ برنامه از `FLAG_SECURE` استفاده می‌کند و این تصاویر capture زنده نیستند. میزبان، مسیرها و خروجی‌های نمایشی ساختگی‌اند. هیچ اتصال، بارگذاری فایل یا ویژگیِ تأییدنشده‌ای در ویدیو به‌عنوان رویداد واقعی نشان داده نمی‌شود.

فونت Vazirmatn تحت SIL Open Font License 1.1 است و متن مجوز در `Vazirmatn-OFL.txt` آمده است. MP4ها، تصاویر بازسازی‌شده و پروژهٔ رسانه مجوز Apache-2.0 سورس برنامه را به ارث نمی‌برند. موسیقی کلیپ قدیمی ۲۸ثانیه‌ای عمداً در این نسخه استفاده نشده است؛ این خروجی بی‌صداست.
