# حافظهٔ ماندگار ChatGPT — پروژهٔ Laleh

آخرین به‌روزرسانی: ۱۵ مهر ۱۴۰۵ / ۷ اکتبر ۲۰۲۶ (Asia/Tehran)

این فایل، زمینه و گزارش فعالیت ماندگار پروژه برای ادامهٔ کار در ChatGPT، Codex و ابزارهای مشابه است. حافظهٔ داخلی حساب ChatGPT از داخل مخزن قابل‌ویرایش نیست؛ بنابراین در هر نشست جدید، این فایل باید همراه با `README.md` و دستورهای مرتبط در `docs/codex/` خوانده شود.

## شناسه و هدف پروژه

- مخزن: `sinicablekaveh-create/Laleh`
- شاخهٔ اصلی: `main`
- نام برنامه: Telegram Electric / Laleh
- نوع پروژه: برنامهٔ Android با Java و TDLib
- هدف: ورود کاربر به حساب تلگرام، مدیریت اتصال و پروکسی، جست‌وجوی مخاطب و گروه، بانک آفلاین واژه‌های برق، پردازش محلی نتایج و ارسال کنترل‌شده به گروه‌های مجاز

## وضعیت فنی ثبت‌شده

- نسخهٔ برنامه: `1.13.0`
- `versionCode`: `15`
- namespace و application ID: `com.sinicable.telegramelectric`
- حداقل Android: API 26
- compile/target SDK: API 35
- ABI بستهٔ فعلی: `arm64-v8a`
- Android Gradle Plugin: `8.7.3`
- Java: 17
- Gradle مورد استفاده در CI و محیط ابری: 8.9
- CI نسخهٔ Android Build Tools 35.0.0 را صریحاً نصب می‌کند؛ نسخهٔ 34.0.0 نیز در محیط ابری آماده‌شده به‌عنوان نیاز جانبی Android Gradle Plugin نصب شد.
- وابستگی اصلی تلگرام: `io.github.tdlib-android:core:0.1.1`
- Gradle Wrapper 8.9 در مخزن وجود دارد و فرمان مرجع `./gradlew` است.

## قابلیت‌های موجود

- چرخهٔ احراز هویت TDLib با API ID و API Hash واردشده توسط کاربر
- ورود شماره، کد تلگرام و رمز دومرحله‌ای
- پشتیبانی از لینک‌های پروکسی MTProto و SOCKS5
- بانک واژهٔ فارسی و انگلیسی مرتبط با برق، ویرایش دستی و یادگیری محلی
- جست‌وجوی شماره با نرمال‌سازی ارقام و قالب‌های رایج ایران
- دریافت مخاطبین تلگرام بدون افزودن مخاطب و بدون دسترسی به دفترچه تلفن Android
- جست‌وجوی مرحله‌ای گروه‌های عمومی با صف قابل توقف و ادامه
- انتخاب گروه هدف و کنترل مجوز واقعی ارسال متن و عکس
- اجرای هسته در پس‌زمینه، زمان‌بندی ارسال و رعایت محدودیت‌های Telegram
- خروجی TXT و Excel و نگهداری عکس‌های انتخاب‌شده
- تست‌های واحد برای هسته، جست‌وجو، بانک واژه، چرخهٔ Activity و بخش‌های اتصال تلگرام

## نقشهٔ معماری فعلی

- `TelegramClientManager` درگاه واحد TDLib، احراز هویت، updateها و cache اطلاعات تلگرام است.
- `CentralCore` جست‌وجو، زمان‌بندی و چرخهٔ ارسال را هماهنگ می‌کند.
- `WordBank` واژه‌های محلی و `SmartSearchQueue` تنظیمات، پیشرفت و تاریخچهٔ جست‌وجو را نگهداری می‌کنند.
- `BackgroundRuntime` همان نمونه‌های درحال‌اجرای `CentralCore` و `TelegramClientManager` را با `BackgroundCoreService` به اشتراک می‌گذارد؛ سرویس پس‌زمینه نباید client یا session جدا بسازد.
- رابط اصلی به‌صورت programmatic و راست‌به‌چپ در `MainActivity`، `CentralCorePanel` و viewهای سفارشی ساخته می‌شود؛ پروژه layout XML یا Navigation graph ندارد.

## اصولی که در تغییرات بعدی باید حفظ شوند

1. `TelegramClientManager` و چرخهٔ فعلی احراز هویت حفظ شود.
2. برنامه فقط یک TDLib client/session داشته باشد؛ نشست موازی یا جایگزین ساخته نشود.
3. سازگاری داده‌های ذخیره‌شده، cache و قابلیت‌های موجود حفظ شود.
4. پایگاه داده یا فایل‌های خصوصی برنامهٔ رسمی Telegram خوانده نشود.
5. پیام خصوصی، شمارهٔ تلفن، API Hash، کد ورود، رمز دومرحله‌ای، کلید رمزنگاری و فایل نشست هرگز در Git، گزارش‌ها یا این فایل ثبت نشود.
6. موفقیت تست یا build فقط وقتی اعلام شود که همان فرمان واقعاً اجرا و نتیجه بررسی شده باشد.
7. پاسخ واقعی سرور Telegram و رفتار رابط روی گوشی فقط پس از آزمایش روی دستگاه و حساب آزمایشی قابل تأیید است.

## مرز داده‌های حساس روی دستگاه

- `AuthSessionStore` مقادیر API ID و API Hash را در SharedPreferences خصوصی برنامه با نام `telegram_auto_login` نگهداری می‌کند. این مقادیر نباید از دستگاه استخراج یا در Git، حافظه، log و artifact ثبت شوند.
- `TelegramClientManager` می‌تواند نام، شماره و دادهٔ جست‌وجو را در SharedPreferences خصوصی `telegram_discovery` نگهداری کند؛ این داده‌ها شخصی‌اند.
- TDLib داده‌های نشست را زیر پوشهٔ خصوصی `filesDir/tdlib` نگهداری می‌کند. پوشهٔ نشست و فایل‌های آن نباید کپی، commit یا ضمیمه شوند.
- خروجی‌های TXT/Excel ممکن است نام، شماره و لینک گروه داشته باشند و نباید به‌عنوان fixture یا مدرک build عمومی شوند.
- گزارش خام تست و lint، logها، شناسه‌های گروه، عکس‌ها و مسیرهای محلی ممکن است داده یا جزئیات محیط را فاش کنند؛ فقط خلاصهٔ عددی پاک‌سازی‌شده در حافظه ثبت شود.
- `AndroidManifest.xml` در وضعیت فعلی `allowBackup=false` دارد.
- پیکربندی فعلی TDLib از `databaseEncryptionKey` خالی استفاده می‌کند. تغییر این رفتار نیازمند بررسی migration و یک وظیفهٔ امنیتی جداگانه است؛ در جریان کار عادی نباید بدون طرح مهاجرت تغییر کند.

## راه‌اندازی و اعتبارسنجی محیط توسعه

فرمان مرجع مخزن برای تست و ساخت:

```bash
gradle --no-daemon --stacktrace testDebugUnitTest assembleDebug
```

فرمان کامل مشابه CI:

```bash
gradle --no-daemon --stacktrace testDebugUnitTest
gradle --no-daemon --stacktrace lintDebug
gradle --no-daemon --stacktrace assembleDebug
```

در محیط ابری آماده‌شده در ۷ اکتبر ۲۰۲۶، ابتدا این فایل فعال‌سازی خوانده شد:

```bash
source /workspace/toolchains/activate.sh
```

سپس فرمان زیر اجرا شد:

```bash
gradle --no-daemon --max-workers=4 -x generateAppLogo \
  testDebugUnitTest lintDebug assembleDebug
```

در این محیط، `generateAppLogo` کنار گذاشته شد چون اجرای آن محتوای فایل tracked به نام `app/src/main/res/drawable/app_logo.jpg` را تغییر می‌دهد. همچنین Gradle هنگام بسته‌بندی، APK قدیمی tracked به نام `app/build/outputs/apk/debug/Laleh-1.13.0-arm64.apk` را حذف می‌کند؛ پیش از build ابری باید نسخهٔ اصلی آن بیرون checkout نگهداری و پس از build دقیقاً بازیابی شود. این دو مورد نباید با `git reset --hard` یا بازنویسی تغییرات کاربر حل شوند.

## شواهد آخرین اعتبارسنجی

اعتبارسنجی روی baseline با commit `ef701a9b584e31012962d88b43b7de2f0ef341d1` انجام شد:

- `testDebugUnitTest`: تعداد ۱۰۳ تست، ۰ شکست، ۰ خطا، ۰ تست ردشده
- `lintDebug`: موفق، همراه با ۳۷ هشدار
- `assembleDebug`: موفق
- APK تولیدشده: `app/build/outputs/apk/debug/app-debug.apk` با اندازهٔ تقریبی ۲۴ مگابایت
- build محلی با JDK 17، Gradle 8.9 و Android SDK 35 انجام شد.
- نصب APK، ورود واقعی تلگرام و آزمون کامل روی دستگاه اجرا نشده است.
- اعداد ۹۳ تست و ۳۱ هشدار در `CHANGE_REPORT.fa.md` مربوط به اجرای قدیمی‌ترند؛ برای وضعیت فعلی باید نتیجهٔ اجرای جدید دوباره بررسی شود.

## گزارش فعالیت ثبت‌شده

### ۱۰ اکتبر ۲۰۲۶ — Phase 1.1: Laleh+T workspace bootstrap

- نام Gradle workspace به `LalehT` تغییر کرد و ماژول‌های افزایشی `telegram-core` و `laleh-core` اضافه شدند؛ اپ موجود و `TelegramClientManager` جایگزین یا جابه‌جا نشدند.
- `telegram-core` فقط contract مالک یگانهٔ session و مرز وابستگی TDLib را دارد و عمداً TDLib client نمی‌سازد. `laleh-core` مرز دادهٔ آفلاین Laleh را ثبت می‌کند؛ storage فعلی برنامه دست‌نخورده است.
- Gradle Wrapper 8.9، script بررسی محیط، مستندات معماری/build/security/roadmap، و CI مبتنی بر Wrapper با artifact سازگار `telegram-electric-debug` اضافه شدند. CI همچنان JDK 17 و Android SDK 35 را نصب و test/lint/assemble را اجرا می‌کند.
- در محیط این اجرا Temurin JDK 17.0.20.1 و Android SDK Platform/Build Tools 35.0.0 نصب و script محیط با موفقیت اجرا شد. NDK لازم نیست، زیرا TDLib به شکل dependency از پیش‌ساخته مصرف می‌شود.
- اجرای محلی `./gradlew testDebugUnitTest` به validation نرسید: Gradle در این محیط قادر به resolve کردن Android Gradle Plugin از Google/Maven/Plugin Portal نبود (اتصال Java به proxy رد شد). هیچ نتیجهٔ موفق test/lint/build برای این تغییر ثبت نشده است؛ CI pull request باید این سه gate را اجرا کند.
- پس از بازبینی PR، script bootstrap انتخاب `JAVA_HOME` را با مسیر `java` هماهنگ می‌کند و وجود واقعی `aapt2` در Build Tools 35.0.0 را بررسی می‌کند؛ نام artifact به `telegram-electric-debug` برای سازگاری با مصرف‌کنندگان قبلی برگردانده و ادعای قدیمی نبودن Wrapper اصلاح شد.

### ۷ اکتبر ۲۰۲۶ — آماده‌سازی محیط ابری

- مخزن و مستندات build، workflowها، نسخه‌های ابزار و تست‌ها بررسی شد.
- دسترسی خواندن GitHub با `git ls-remote` تأیید شد.
- Temurin JDK 17.0.16، Gradle 8.9، Android Command-line Tools، SDK Platform 35 و Build Tools 35/34 نصب شدند.
- checksum فایل‌های دانلودی بررسی شد و اعتبارسنجی TLS غیرفعال نشد.
- trust store محیط و cache قابل‌نوشتن Android/Gradle برای proxy محیط تنظیم شد.
- اجرای دوبارهٔ نصب موفق بود.
- ۱۰۳ تست، lint و ساخت APK با موفقیت کامل شدند.
- تغییرات ناخواستهٔ build روی APK tracked بازیابی شد و checkout تمیز باقی ماند.
- راهنمای نصب و شروع قابل‌استفادهٔ مجدد در draft محیط ابری ذخیره شد؛ انتشار نهایی محیط بر عهدهٔ کاربر است.

### ۷ اکتبر ۲۰۲۶ — بررسی GitHub

- شاخهٔ محلی `work` در زمان بررسی با `origin/main` و commit `ef701a9` یکسان بود.
- آخرین workflow مشاهده‌شدهٔ `Build Android APK` با موفقیت پایان یافته بود.
- ارسال `git push origin HEAD:main` اجرا شد و GitHub پاسخ `Everything up-to-date` داد.
- فایل‌های cache، خروجی‌های موقت build و داده‌های خصوصی برنامه عمداً به GitHub ارسال نشدند.
- CodeRabbit برای این آماده‌سازی اجرا نشد، زیرا هیچ تغییر کدی برای review وجود نداشت و CLI آن نیز در محیط نصب نبود.

### ۷ اکتبر ۲۰۲۶ — سخت‌سازی CI برای pull requestها و Node 24

- baseline مرجع `ci-baseline-ef701a9` بررسی شد و همچنان دقیقاً روی commit `ef701a9b584e31012962d88b43b7de2f0ef341d1` باقی ماند.
- `main` پیش از تغییر فقط دو commit مستنداتی جلوتر از baseline بود و تغییر کد Android/TDLib نسبت به baseline نداشت.
- در workflow `Build Android APK` یک شکاف CI شناسایی شد: validation روی `pull_request` شاخهٔ `main` اجرا نمی‌شد.
- PR شمارهٔ ۲۳ با عنوان `ci: validate Android builds on pull requests` ایجاد شد و پس از validation موفق به‌صورت squash ادغام شد.
- commit نهایی روی `main`: `3baf685566d78c5c0b1514a014fe4ea35165fd66` با عنوان `ci: validate PR builds on Node 24`.
- workflow اکنون برای PRهای `main` نیز تست واحد، lint و ساخت APK را اجرا می‌کند و با `concurrency` اجرای تکراری هم‌زمان را لغو می‌کند.
- actionهای GitHub به نسخه‌های Node 24-native به‌روزرسانی شدند: `actions/checkout@v5`، `gradle/actions/setup-gradle@v5` و `actions/upload-artifact@v6`.
- validation روی commit نهایی PR: `testDebugUnitTest` موفق، `lintDebug` موفق، `assembleDebug` موفق و artifact `telegram-electric-debug` با موفقیت آپلود شد؛ هشدار قبلی Node 20 در اجرای جدید مشاهده نشد.
- workflow پس از merge روی commit `3baf685` نیز با موفقیت کامل شد.
- CodeRabbit به دلیل کمتر از ۱۰ ستاره بودن مخزن review خودکار اجرا نکرد؛ فرمان `@coderabbitai review` روی PR #23 به‌صورت دستی ارسال شد.
- هیچ کد Android/TDLib، دادهٔ کاربر، credential یا session در این تغییر اصلاح یا ثبت نشد.

### ۷ اکتبر ۲۰۲۶ — ساخت APK اشکال‌زدایی

- فرمان `gradle --no-daemon --max-workers=4 -x generateAppLogo assembleDebug` با JDK 17 و Gradle 8.9 اجرا شد.
- Gradle با وضعیت `BUILD SUCCESSFUL` در ۷ ثانیه پایان یافت؛ ۳۴ task به‌روز بودند.
- خروجی تأییدشده: `app/build/outputs/apk/debug/app-debug.apk`، حدود ۲۴ مگابایت، SHA-256: `21fa88aeea13bcf7edff6d6c66d61f1f39fe974ef81f51bafb81606b085230b9`.
- محتوای APK شامل `classes.dex`، `AndroidManifest.xml` و کتابخانهٔ `lib/arm64-v8a/libtdjni.so` بررسی شد.
- تست‌های واحد و lint در این اجرای کوتاه دوباره اجرا نشدند؛ نتیجهٔ ۱۰۳ تست و lint پیشین در بخش اعتبارسنجی باقی است.
- APK قدیمی tracked پیش از build پشتیبان‌گیری و بعد از build بازیابی شد؛ هیچ فایل tracked دیگری تغییر نکرد.

### ۷ اکتبر ۲۰۲۶ — تکمیل TASK-001: audit مخزن

- معماری واقعی Android، Gradle، TDLib، جست‌وجوی گروه، صف واژه، ذخیره‌سازی، سرویس پس‌زمینه، تست‌ها و CI بررسی شد.
- گزارش شواهد در `docs/codex/TASKS/TASK-001-AUDIT.md` ثبت شد؛ این مرحله رفتار محصول را تغییر نداد.
- فرمان `gradle --no-daemon --max-workers=4 --rerun-tasks -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` با موفقیت اجرا شد: ۱۰۳ تست، ۰ شکست، ۰ خطا، ۰ ردشده؛ lint با ۰ خطا و ۳۷ هشدار؛ APK اشکال‌زدایی ساخته شد.
- فقط `TelegramClientManager` در کد تولیدی TDLib client می‌سازد. مسیر بازیابی `BackgroundCoreService` هنگام نبود `BackgroundRuntime` باید در مراحل بعد با آزمون چرخهٔ حیات محافظت شود تا یک نشست فعال حفظ شود.
- شواهد و commitها در شاخهٔ `codex/task-001-audit` روی GitHub ارسال شدند. ساخت خودکار PR از این محیط به‌دلیل پاسخ `Forbidden` از GitHub GraphQL انجام نشد.

### ۷ اکتبر ۲۰۲۶ — تکمیل TASK-002: هستهٔ جست‌وجوی گروه

- خروجی جست‌وجوی گروه در `TelegramClientManager` اکنون شناسه‌ها را با ترتیب دریافت حفظ می‌کند و شناسهٔ تکراری را پیش از capture و شمارش نادیده می‌گیرد؛ این محافظ در برابر callback یا مسیر بازیابی تکراری است.
- تست بازگشت `groupSearchCollectionReportsARepeatedGroupOnlyOnce` تضمین می‌کند یک گروه تکراری فقط یک‌بار در callback و شمارش نتیجه ظاهر شود.
- تست هدفمند `TelegramPhoneSearchTest` با ۲۰ تست موفق شد. اجرای کامل `testDebugUnitTest lintDebug assembleDebug` نیز با ۱۰۴ تست موفق، صفر failure/error/skip و lint بدون error (۳۸ warning) موفق شد.
- commit تغییر کد: `400eaad4ea31431d1ec69f37d2316c5ff63adde0` در شاخهٔ `codex/task-001-audit`.

### ۷ اکتبر ۲۰۲۶ — ادامهٔ زنجیره: TASK-003 و TASK-004

- TASK-003: seedهای شهری و نرمال‌سازی در `d7feb0e`؛ تست migration بانک نسخهٔ ۱، حفظ حذف‌ها و بانک خالی در `e74b3ee` ثبت شد.
- ادعای قبلی موفقیت کامل TASK-003 در اجرای تازه بازتولید نشد: هفت تست یکپارچه fixture نسخهٔ ۱ داشتند. پس از اصلاح fixture به نسخهٔ ۲ و افزودن تست واقعی migration، suite کامل موفق شد.
- TASK-004 در `a77f588`: بازیابی نتایج، cache تازه را دوباره بررسی می‌کند تا GetChat اضافی حذف شود؛ پاسخ چت با شناسهٔ نامرتبط رد می‌شود.
- فرمان `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` واقعاً اجرا شد: ۱۱۰ تست، بدون failure/error/skip؛ lint با ۳۷ warning و بدون error؛ APK ساخته شد. آزمون دستگاه و سرور واقعی اجرا نشده است.
- ابزار Codex Tasks قابل‌فراخوانی در این نشست در دسترس نبود؛ پیگیری در فایل‌های وظایف مخزن ادامه یافت.

### ۷ اکتبر ۲۰۲۶ — TASK-005: فیلتر گروه‌ها

- فیلتر نوع TDLib مستقیم شده و شناسهٔ صفر رد می‌شود. تست پاسخ مختلط شامل گروه پایه، سوپرگروه، کانال، چت خصوصی/secret، نوع نامشخص و شناسهٔ تکراری است.
- فرمان `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` با ۱۱۱ تست و بدون failure/error/skip موفق شد؛ lint و ساخت APK نیز موفق بودند. آزمون واقعی دستگاه اجرا نشده است.

### ۷ اکتبر ۲۰۲۶ — TASK-006: رتبه‌بندی مرتبط بودن

- GroupRanker موجود به callback واقعی جستجو متصل شد؛ عنوان دقیق، عبارت کامل، username و tokenهای موضوع/شهر در query امتیاز دارند. تعداد اعضا در امتیاز دخالت ندارد؛ امتیاز برابر ترتیب دریافت را حفظ می‌کند.
- تست‌ها نرمال‌سازی فارسی/عربی، Locale ترکی، query خالی و رتبه‌بندی callback را بررسی می‌کنند. فرمان `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` با ۱۱۵ تست بدون failure/error/skip و lint/build موفق اجرا شد.
- وزن‌ها heuristic هستند؛ ارزیابی relevance با دادهٔ واقعی و آزمون دستگاه اجرا نشده است.

### ۷ اکتبر ۲۰۲۶ — TASK-007: وضعیت رابط جستجو

- نشانگر اجرای جستجو، پیام انتظار و راهنمای شروع در تاریخچهٔ خالی، و پیام متفاوت برای پاسخ موفق بدون گروه اضافه شد.
- تست UI وضعیت running/stopped، پاسخ بدون نتیجه و Activity نابودشده را پوشش می‌دهد؛ تست‌های موجود توقف و پاسخ دیررس نیز اجرا شدند.
- فرمان `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` با ۱۱۸ تست بدون failure/error/skip و lint/build موفق اجرا شد. آزمون بصری روی دستگاه اجرا نشده است.

### ۷ اکتبر ۲۰۲۶ — TASK-008: پایهٔ وب

- workspace مستقل `web/` با Next.js، React، TypeScript و npm lockfile در `86c3834` اضافه شد. صفحهٔ فارسی RTL، جستجوی GET نرمال‌شده و محدود به ۹۶ نویسه، و وضعیت catalog خالی دارد.
- `npm test` با ۲ تست، `npm run typecheck` و `NEXT_TELEMETRY_DISABLED=1 npm run build` موفق شدند. smoke HTTP سرور تولیدی نیز RTL، query و وضعیت خالی را تأیید کرد؛ سرور آزمایشی پس از بررسی متوقف شد.
- catalog عمومی هنوز خالی است و سرویس remote یا deployment پیکربندی نشده؛ بررسی بصری مرورگر اجرا نشده است. وب به نشست یا احراز هویت TDLib دسترسی ندارد.

### ۷ اکتبر ۲۰۲۶ — TASK-009: پایهٔ sync عمومی

- DiscoveryMetadata فقط فیلدهای عمومی مشخص دارد؛ username عمومی و طول عنوان/دسته/شهر و revision کنترل می‌شوند. DiscoverySyncQueue از preferences موجود discovery استفاده می‌کند، پیش‌فرض غیرفعال است، حداکثر ۱۰۰ رکورد نگه می‌دارد، revision قدیمی را رد و ack قدیمی را از حذف revision جدید منع می‌کند.
- قطع opt-in صف را پاک می‌کند. تست‌ها ماندگاری، ظرفیت، opt-out، schema نامعتبر و whitelist فیلدها را پوشش می‌دهند.
- فرمان `gradle --no-daemon --max-workers=4 -x generateAppLogo testDebugUnitTest lintDebug assembleDebug` با ۱۲۲ تست بدون failure/error/skip و lint/build موفق اجرا شد.
- endpoint/transport خارجی وجود ندارد و هیچ sync شبکه‌ای انجام نمی‌شود؛ اتصال UI و delivery در مراحل بعد باقی است.

### ۷ اکتبر ۲۰۲۶ — TASK-010: baseline کارایی

- در `b405814` سه regex نرمال‌سازی WordBank یک‌بار compile می‌شوند؛ benchmark مصنوعی قابل‌اجرای مجدد در tools ثبت شد.
- سه دور ۲۰هزار نرمال‌سازی روی میزبان: پیش از تغییر ۶۳٫۹۹/۴۱٫۴۷/۴۴٫۷۲ ms؛ پس از تغییر ۴۱٫۸۵/۲۳٫۰۹/۳۱٫۰۴ ms؛ checksum هر دو 931680. این اندازه‌گیری نتیجهٔ دستگاه Android یا benchmark آماری نیست.
- فرمان کامل تست/lint/build با ۱۲۲ تست بدون failure/error/skip موفق شد؛ جزئیات فرمان‌ها در TASK-010 ثبت شده است.

### ۷ اکتبر ۲۰۲۶ — TASK-011: تحلیل query

- SearchQuery در مسیر واقعی جستجوی TDLib به‌کار می‌رود: نرمال‌سازی، حد ۹۶ codepoint، تشخیص دستهٔ برق و پنج شهر، intent جستجو/آموزش/بازار و حداکثر ۱۶ keyword یکتا. دسته/شهر ناشناخته خالی می‌ماند و هیچ inference شبکه‌ای انجام نمی‌شود.
- فرمان کامل تست/lint/build با ۱۲۵ تست بدون failure/error/skip موفق شد؛ آزمون query فارسی/عربی، boundary شهر، query خالی، Unicode و محدودیت‌ها اجرا شد.

### ۷ اکتبر ۲۰۲۶ — TASK-012: پیشنهادهای مرتبط

- GroupKeywordBank و SmartKeywordQueue موجود به WordBank و UI متصل شدند؛ پیشنهاد موضوع مرتبط شهر query را حفظ می‌کند، نرمال و یکتا است و به ۸ مورد محدود می‌شود.
- UI فقط با انتخاب صریح کاربر پیشنهاد را به بانک می‌افزاید؛ پیشنهاد دادن بانک یا حذف‌های کاربر را خودکار تغییر نمی‌دهد.
- فرمان کامل تست/lint/build با ۱۲۸ تست بدون failure/error/skip موفق شد؛ شهر، Arabic variants، ظرفیت و حذف/افزودن دستی پوشش داده شدند.

### ۷ اکتبر ۲۰۲۶ — TASK-013: هماهنگی discovery

- queryهای عمومی نرمال‌شدهٔ یکسان و هم‌زمان برای همان client یک درخواست TDLib دارند؛ هر subscriber پاسخ یا خطا را یک‌بار می‌گیرد. subscriber ناموفق دریافت دیگران را مختل نمی‌کند و عملیات تمام‌شده از map حذف می‌شود.
- منابع محدود به ۱۶ عملیات و ۳۲ subscriber اضافه برای هر عملیات هستند؛ ترتیب قفل‌ها برای callback هم‌زمان بازبینی شد. discovery مرتبط از پیشنهادهای curated و انتخاب کاربر TASK-012 استفاده می‌کند.
- فرمان کامل تست/lint/build با ۱۳۰ تست بدون failure/error/skip موفق شد؛ تست coalescing فارسی/عربی، پاسخ تکراری، cleanup و خطای subscriber اجرا شد.

### ۷ اکتبر ۲۰۲۶ — TASK-014: آمار محلی جستجو

- SearchMetrics snapshot عددی و synchronized از درخواست/پاسخ/خطا/timeout، cache/coalescing، تعداد نتیجه و زمان پاسخ فراهم می‌کند. هیچ query، شناسه، عنوان، شماره یا دادهٔ نشست در metrics ذخیره نمی‌شود و آمار شبکه‌ای ارسال نمی‌شود.
- تست فیزیکی coalescing یک درخواست و یک completion برای چند subscriber را تأیید می‌کند؛ snapshot قبلی immutable باقی می‌ماند. فرمان کامل تست/lint/build با ۱۳۱ تست بدون failure/error/skip موفق شد.

### ۷ اکتبر ۲۰۲۶ — TASK-015: بهبود رتبه‌بندی

- GroupRanker برای عنوان از boundary عبارت، نرمال‌سازی punctuation و وزن صریح category/location استفاده می‌کند؛ substring تصادفی حذف شد. tieها اکنون با group ID مرتب می‌شوند و ترتیب cache recovery تعیین‌کننده نیست.
- شواهد relevance از نمونه‌های مصنوعی deterministic است، نه داده یا ارزیابی واقعی کاربر. فرمان کامل تست/lint/build با ۱۳۲ تست بدون failure/error/skip موفق شد.

### ۷ اکتبر ۲۰۲۶ — TASK-016: قرارداد پایدار داده

- schema v2 عمومی در shared ثبت شد؛ group ID و revision به رشتهٔ decimal تبدیل شدند تا دقت Long در JavaScript حفظ شود. Android schema v1 را همچنان می‌خواند؛ وب فقط اعداد safe-integer نسخهٔ ۱ را migrate می‌کند.
- parser وب whitelist، range، نوع و schema را کنترل می‌کند و record immutable برمی‌گرداند. مرزهای Long.MIN/MAX و نسخهٔ قدیمی تست شدند.
- فرمان کامل Android با ۱۳۳ تست بدون failure/error/skip و lint/build موفق؛ وب با ۴ تست موفق، typecheck و build تولیدی موفق اعتبارسنجی شد.

### ۷ اکتبر ۲۰۲۶ — TASK-017: index عمومی

- interface مستقل DiscoveryIndex و index مشتق‌شدهٔ tokenهای عمومی در وب اضافه شد؛ حداکثر ۱۰هزار رکورد، query prefix/intersection، revision monotonic، حذف posting قدیمی و صفحه‌های immutable با حداکثر ۵۰ hit دارد.
- `npm test` با ۷ تست، typecheck و build تولیدی موفق شدند. تست‌ها synthetic هستند؛ catalog واقعی و adapter سرویس remote هنوز وجود ندارد.

### ۷ اکتبر ۲۰۲۶ — TASK-018: cache عمومی

- cache پایدار عمومی Android در preferences موجود، حداکثر ۲۰۰ رکورد و TTL حداکثر یک روز دارد؛ expiry، برگشت ساعت، revision قدیمی، LRU و clear/invalidate بررسی شدند.
- cache پاسخ وب محدود به ۲۰۰ entry، ۵۰ hit و key به طول ۵۱۲ است؛ ورودی و خروجی copy می‌شوند و TTL/LRU/clear دارد.
- Android با ۱۳۵ تست بدون failure/error/skip و lint/build موفق؛ وب با ۹ تست موفق، typecheck و build تولیدی موفق اعتبارسنجی شد. اتصال به provider/delivery در مراحل بعد باقی است.

### ۷ اکتبر ۲۰۲۶ — TASK-019: pipeline محلی sync

- DiscoverySyncRunner با transport تزریقی، scheduler/clock تزریقی، اجرای تک‌درخواست، deadline سی‌ثانیه‌ای و completion یک‌بار اضافه شد. retry/backoff در preferences موجود می‌ماند؛ rejection دائمی revision را pause می‌کند و revision تازه retry را reset می‌کند.
- تست‌های timeout/پاسخ دیررس، ack قدیمی، callback تکراری، retry پس از reconstruction و opt-out اجرا شدند. فرمان کامل تست/lint/build با ۱۳۸ تست بدون failure/error/skip موفق شد.
- TASK-019 فقط از نظر pipeline محلی پیاده و اعتبارسنجی شده؛ sync واقعی مسدود است چون endpoint مجاز، سیاست authentication و منبع catalog عمومی تعیین نشده‌اند. هیچ delivery شبکه‌ای اجرا نشده است.

### ۷ اکتبر ۲۰۲۶ — TASK-020 و وضعیت توقف زنجیره

- در `cd98a16` validation Android/وب برای فیلد ناشناخته/خصوصی، ID غیرصحیح، schema/username نامعتبر، نویسهٔ کنترلی عنوان، revision ناسازگار cache و username تکراری index سخت‌تر شد. JSON صف محدود شده و rejection دائمی pause باقی می‌ماند.
- آخرین validation واقعی: فرمان کامل Android با ۱۳۹ تست، صفر failure/error/skip، lint با ۳۵ warning و بدون error، APK موفق؛ وب با ۱۰ تست، typecheck و build تولیدی موفق. `npm audit --audit-level=high` صفر vulnerability گزارش کرد.
- TASK-001 تا TASK-018 تکمیل‌اند؛ TASK-019 فقط pipeline محلی اعتبارسنجی شده و sync واقعی به endpoint/authentication/catalog مجاز نیاز دارد. TASK-020 به‌عنوان hardening مستقل انجام شد؛ TASK-021 تا TASK-055 pending هستند.
- وضعیت قابل‌ادامه در `docs/codex/CHAIN_STATUS.md` ثبت شده؛ سؤال پیکربندی سرویس به کاربر ارسال شد. مجوز کل زنجیره برقرار است و تأیید عمومی دوباره لازم نیست. secret/token نباید در chat یا Git ثبت شود.

### تاریخچهٔ نزدیک پروژه پیش از ثبت این حافظه

- `ef701a9`: اعتبارسنجی lint پیش از ساخت APK در CI
- `658b61d`: بازیابی README پس از حذف تصادفی
- `10fa10b`: ادغام pull request شمارهٔ ۱۹
- `25edc40`: ادغام زنجیرهٔ اجرای Codex
- `8e66685`: ادغام اصلاح baseline compilation
- `1dd8506`: مقاوم‌سازی عملیات کارت نتایج جست‌وجوی شماره
- `2b36eba`: اجرای اعتبارسنجی کامل Laleh در شاخهٔ اجرایی
- `370c2cc`: بازیابی compilation نتیجهٔ جست‌وجوی شماره
- `24c618e`: افزودن بستهٔ اجرایی TASK-001 تا TASK-055

Git history مرجع قطعی تمام تغییرات و نویسندگان است؛ این بخش صرفاً خلاصهٔ قابل‌خواندن برای ادامهٔ کار است.

## وضعیت GitHub هنگام ثبت

در زمان بررسی، pull requestهای باز شمارهٔ ۲، ۳، ۴، ۱۲، ۲۰ و ۲۲ مشاهده شدند. این فهرست موقتی است و پیش از هر تصمیم merge یا review باید مستقیماً از GitHub دوباره خوانده شود.

### 2026-10-07 — TASK-021 و تصمیم سرویس

- کاربر تأیید کرد سرویس اختصاصی ندارد. نشانی مستندات صحیح تلگرام `https://core.telegram.org` است؛ این نشانی مقصد ارسال metadata نیست. زنجیره با قراردادها و فهرست محلی ادامه می‌یابد؛ همگام‌سازی شبکه‌ای غیرفعال باقی می‌ماند.
- TASK-021: جست‌وجوی وب به CatalogService و index عمومی محلی وصل شد؛ cache محدود، صفحه‌بندی و حالت خالی واقعی اضافه شدند. فهرست تأییدشده فعلاً خالی است؛ دادهٔ ساختگی منتشر نشده است. commit: `be9bd48`.
- اجرا: `cd web && npm test`، ۱۲ تست موفق؛ `npm run typecheck` موفق؛ `NEXT_TELEMETRY_DISABLED=1 npm run build` موفق. HTTP تولیدی با query فارسی، زبان fa، جهت rtl، فرم جست‌وجو و پیام فهرست خالی بررسی شد؛ سرور آزمایشی سپس متوقف شد.
- کد اندروید در این وظیفه تغییر نکرد؛ تست دستگاه، تلگرام زنده و بررسی بصری مرورگر اجرا نشدند. TASK-022 تا TASK-055 هنوز انجام نشده‌اند.

### 2026-10-07 — TASK-022: صفحه‌های عمومی discovery

- صفحه‌های دسته‌بندی، نتایج دسته‌بندی و جزئیات گروه در وب به index عمومی محلی متصل شدند. نمایش کارت‌ها مشترک است و فقط title، username، category و location عمومی را می‌خواند؛ ارتباط با Telegram client یا دادهٔ خصوصی ندارد. commit: `9ab13f7`.
- اجرا: `cd web && npm test` با ۱۳ تست موفق و بدون شکست/skip؛ `npm run typecheck` و `NEXT_TELEMETRY_DISABLED=1 npm run build` موفق. build مسیرهای `/categories`، `/categories/[name]` و `/groups/[id]` را تولید کرد.
- فهرست تأییدشده هنوز خالی است؛ دادهٔ نمونه منتشر نشده و آزمایش دستگاه/تلگرام زنده/بصری مرورگر اجرا نشده‌اند.

## فایل‌های مرجع

- `README.md`: معرفی محصول، حریم خصوصی، قابلیت‌ها و فرمان build
- `AGENTS.md`: الزام خواندن و به‌روزرسانی این حافظه برای عامل‌های آینده
- `CHANGE_REPORT.fa.md`: گزارش تغییرات فارسی
- `docs/codex/ARCHITECTURE_RULES.md`: محدودیت‌های معماری
- `docs/codex/EXECUTION_RULES.md`: قواعد اجرای وظایف
- `docs/codex/TEST_PLAN.md`: برنامهٔ تست
- `docs/codex/BUILD_PLAN.md`: برنامهٔ build
- `docs/codex/TASKS/`: مشخصات TASK-001 تا TASK-055
- `.github/workflows/build-apk.yml`: تست، lint و ساخت APK در GitHub Actions
- `.github/workflows/drive-backup.yml`: workflow پشتیبان‌گیری سورس

## روش به‌روزرسانی این حافظه

پس از هر تغییر معنادار، یک ورودی تاریخ‌دار به «گزارش فعالیت» اضافه شود و موارد زیر ثبت شوند:

- هدف و نتیجهٔ واقعی تغییر
- commit یا pull request مرتبط
- فرمان‌های تست/build که واقعاً اجرا شدند و نتیجهٔ عددی آن‌ها
- محدودیت‌ها و آزمون‌های اجرا‌نشده
- تصمیم معماری ماندگار یا migration داده

اطلاعات ورود، دادهٔ خصوصی کاربر، محتویات session، مقدار متغیرهای محرمانه و dump محیط هرگز به این فایل افزوده نشود.


### 2026-10-08 — TASK-023: فیلتر و تجربهٔ جست‌وجوی وب

- Issue #33، شاخه‌ها و PRهای #12/#20/#22/#28/#30/#31/#32 دوباره بررسی شدند؛ گزارش تعارض‌ها در `docs/codex/GITHUB_AUDIT_2026-10-08.md` است. شاخهٔ پیش‌فرض فعلی `codex/laleh-intelligent-search-v2` است؛ `main` جداست.
- شاخهٔ مستقل `codex/task-023-search-experience-20261008` از `8cfbbe5` انتخاب شد چون TASK-022 و وب محلی در آن موجودند. دسته/شهر با تطبیق دقیق نرمال‌شده، مرتب‌سازی عنوان/ارتباط، حفظ فیلتر در URL/صفحه‌بندی، فیلتر صحیح صفحهٔ دسته، کنترل‌های labelدار، skip link و چیدمان موبایل اضافه شدند. کلید cache هش‌شده است تا ورودی Unicode طولانی خطای ظرفیت نسازد.
- `npm test`: ۱۸ موفق، صفر شکست/skip؛ typecheck و build تولیدی موفق. HTTP تولیدی RTL، فیلتر/مرتب‌سازی انتخاب‌شده، صفحه‌بندی و حالت خالی را تأیید کرد؛ کاتالوگ نمونه اضافه نشد.
- Java 17، Gradle 8.9، SDK 35: ۱۳۹ تست Android، صفر failure/error/skip؛ فرمان‌های استاندارد تست/lint/build موفق؛ lint بدون خطا با ۳۶ هشدار؛ APK ساخته و manifest/dex/TDLib arm64 بررسی شد. فایل‌های tracked تولیدشده پس از build بازیابی شدند؛ سورس Android تغییر نکرد.
- CI موجود حفظ و job تست/typecheck/build وب و trigger شاخهٔ جدید اضافه شد. هیچ merge، release، force-push یا تغییر شاخهٔ پیش‌فرض انجام نشد. تست دستگاه، تلگرام زنده و بررسی بصری مرورگر اجرا نشدند. TASK-024 تا TASK-055 همچنان pending هستند.
