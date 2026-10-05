# Telegram Electric

اپ اندروید برای ورود به حساب تلگرام با TDLib و بانک آفلاین واژه‌های مرتبط با برق.

## امکانات

- ورود با API ID و API Hash خود کاربر
- ورود شماره تلفن، کد تلگرام و رمز دومرحله‌ای
- افزودن و فعال‌سازی پروکسی MTProto و SOCKS5 با پیست لینک از Clipboard
- بانک اولیه واژه‌های برق فارسی و انگلیسی
- جست‌وجوی آفلاین واژه‌ها
- افزودن دستی واژه
- یادگیری محلی واژه‌های جدید از پیام‌هایی که به واژه‌های برق نزدیک هستند
- ساخت APK با GitHub Actions

## حریم خصوصی

API Hash و شماره تلفن داخل Repository ذخیره نمی‌شوند. این اطلاعات هنگام اجرا داخل برنامه وارد می‌شوند. بانک واژه نیز روی گوشی ذخیره می‌شود.

## APK

Workflow فایل `.github/workflows/build-apk.yml` روی هر Push مرتبط، نسخه Debug را می‌سازد و Artifact با نام `telegram-electric-debug` منتشر می‌کند.

## نسخه 1.11.0

- تشخیص دقیق عضویت: گروه ترک‌شده، مسدودشده و عضو محدودِ خارج از گروه در گروه‌های حساب نمایش داده نمی‌شود.
- اطلاعات چت‌ها، گروه‌های پایه و سوپرگروه‌ها از Cache به‌روز TDLib خوانده می‌شود؛ جستجو برای هر نتیجه درخواست جداگانه دریافت اطلاعات نمی‌فرستد.
- تغییر عضویت، مدیریت و تعداد اعضا با Updateهای TDLib در فهرست گروه‌ها اعمال می‌شود.
- Updateهای یکسان باعث بازسازی صفحه و ذخیره‌سازی تکراری نمی‌شوند و نمایش نتایج جستجو به‌صورت تجمیعی تازه می‌شود.
- کپی عکس و تولید خروجی TXT/Excel روی Thread جدا انجام می‌شود؛ عکس قبلی تا تکمیل کپی عکس جدید حفظ می‌شود.
- انتخاب نوع و بازه خروجی هنگام بازسازی Activity حفظ می‌شود.

در نسخه 1.11.0 ارسال زمان‌بندی‌شده به مدیران و مالکان عضو محدود بود؛ این محدودیت در نسخه 1.12.0 با کنترل مجوز واقعی ارسال جایگزین شده است.

تست‌های بازگشت با `gradle testDebugUnitTest` اجرا می‌شوند و پیش از ساخت APK در GitHub Actions بررسی می‌شوند. تست نصب، ورود واقعی تلگرام و عملکرد روی گوشی باید جداگانه انجام شود.

## نسخه 1.12.0

- زیر «گروه‌های هدف»، دکمه «انتخاب گروه هدف» فهرست گروه‌های عضو حساب را باز می‌کند؛ انتخاب‌ها روی گوشی حفظ می‌شوند.
- فهرست اصلی و آرشیو حساب به‌صورت صفحه‌بندی‌شده با `loadChats` بارگذاری می‌شوند. گروه‌های کشف‌شده‌ای که حساب عضو آن‌ها نیست، هدف ارسال نمی‌شوند.
- عضو عادی هم با مجوز واقعی Telegram می‌تواند ارسال کند. مجوز متن و عکس و محدودیت شخصی اعضا جداگانه کنترل می‌شود و تغییر مجوزها بلافاصله اعمال می‌شود.
- ساخت پیام عکس با ساختار `InputPhoto` نسخهٔ نصب‌شدهٔ TDLib سازگار شده است.
- START هسته را فعال می‌کند؛ حتی بدون متن یا گروه هدف، جستجوی بانک واژه ادامه دارد. ارسال فقط با متن و گروه انتخاب‌شدهٔ مجاز انجام می‌شود.
- هنگام اجرای هسته، دکمه START به «ثبت تنظیمات» تبدیل می‌شود تا متن جدید ثبت شود. STOP ارسال‌ها و جستجوهای بعدی را متوقف می‌کند و پاسخ دیررس درخواست قبلی وضعیت توقف را تغییر نمی‌دهد.
- ثبت تنظیمات و تغییر فاصلهٔ ارسال، زمان ارسال تعیین‌شده یا انتظار اجباری Telegram را لغو نمی‌کند؛ فاصلهٔ جدید برای چرخهٔ بعدی اعمال می‌شود. گروه انتخاب‌شده‌ای که مجوزش تغییر کرده، همچنان قابل برداشتن از انتخاب است.

منابع رسمی: [TDLib](https://github.com/tdlib/td)، [loadChats](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1load_chats.html)، [مجوزهای چت](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1chat_permissions.html).

## Core lifecycle follow-up

- Central-core processing, search scoring, and queue initialization run on a dedicated Android `HandlerThread`; the panel reads a queue-size snapshot and posts view updates to the UI thread.
- Running, stopped, and fatal-error states are explicit. START retries failed initialization; active settings saves preserve scheduled deadlines and Telegram backoff. Each tick and asynchronous result belongs to one run, so STOP/restart cannot execute stale work.
- Shutdown releases the worker and UI listener, while retaining the saved enabled preference for the existing Activity/process restoration flow.
- Closing a Telegram session clears member targets and chat/user/member caches. Closing during queued startup or client creation cannot recreate the connection, and updates from a previous client cannot repopulate targets. Authentication requests and the joined-group/text/photo permission rules are preserved.

Validate with Java 17, Gradle 8.9, and Android SDK 35: `gradle --no-daemon testDebugUnitTest assembleDebug`. Device authentication, actual delivery, and Samsung A17 testing remain separate checks. Debug APKs require matching signing certificates for in-place updates; back up app data before any installation change that could remove it.

Android references: [HandlerThread](https://developer.android.com/reference/android/os/HandlerThread), [threading](https://developer.android.com/guide/components/processes-and-threads).
