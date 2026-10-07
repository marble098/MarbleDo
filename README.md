# MarbleDo

**Small steps. Meaningful moments.**

MarbleDo is an offline-first Android task and countdown app. The interface defaults to Persian/RTL and can be switched to English/LTR. Tasks, reminders, calendar dates, countdown themes, and preferences remain on-device unless the user explicitly exports a backup.

[فارسی](#ماربلدو--marbledo)

## What is implemented

- **Tasks:** Compose UI, smart Persian/English quick-add parsing, guided multi-step voice task creation (category, title, Persian date, time, and priority), search, filters, pinning, sorting, user-created categories, priorities, checklists, tags, archive/completion, recurring tasks, undo, and task reminders.
- **Calendar:** Persian/Gregorian/Islamic date display, month/agenda/year views, Saturday-first preference, local holiday data, a user-adjustable lunar offset, and daily local occasion notifications.
- **Countdowns:** create countdowns directly with one-tap hour/day/week/month/year presets or a validated date/time, select Persian/Gregorian/Islamic-civil date entry and display, 12 display themes, full-screen focus view, Glance home-screen widget, and a Quick Settings tile for quick-add.
- **Preferences:** app language, RTL/LTR, Persian/Latin/Arabic-Indic numerals, Vazirmatn variable font with role-appropriate weights in Persian UI, light/dark/AMOLED/dynamic themes, font scaling, reduced motion, calendar-category visibility/alerts, and notification setup guidance.
- **Storage:** Room database with explicit v1→v2→v3 migrations, pinned/category task fields, and exported schema configuration; Proto DataStore preferences; WorkManager snapshots; local SAF export/import with optional AES-GCM passphrase encryption.
- **Privacy:** no Firebase, analytics, advertising, or network permission in the app. Android system speech recognition may use an external recognition provider according to the device's configuration.
- **Engineering:** Kotlin/Compose, modular feature/core structure, Koin DI, Navigation 3, R8 for release, StrictMode in debug, unit tests, a baseline-profile/Macrobenchmark test module, dependency-update automation, and signed GitHub Release automation.
- **Font license:** the bundled Vazirmatn variable TTF is from [rastikerdar/vazirmatn](https://github.com/rastikerdar/vazirmatn) and is distributed under SIL OFL 1.1; the license is included in `core/designsystem/licenses/`.

## Build and test

### Requirements

- JDK 17.
- Android SDK Platform **37.2** and Build Tools **37.0.0** (the current catalog selection); accept SDK licenses.
- Linux/macOS: `curl`, `unzip`, and `sha256sum` for the checked Gradle bootstrap script. Windows: `curl.exe` and PowerShell.

The repository's `gradlew` bootstraps the Gradle distribution named in `gradle/wrapper/gradle-wrapper.properties` from the official Gradle service, verifies its published SHA-256 checksum, then runs it. That required Wrapper URL is synchronized from the `gradle` version-catalog pin by `scripts/update_versions.py`. No binary Wrapper JAR is checked in.

```bash
./gradlew :app:assembleDebug
./gradlew \
  :core:domain:test \
  :core:data:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :feature:tasks:testDebugUnitTest \
  :feature:calendar:testDebugUnitTest \
  :feature:countdown:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`. Room schema exports are configured under `core/data/schemas/`.

### Version catalog

All dependency, plugin, and Android SDK package pins live in `gradle/libs.versions.toml`. The Gradle Wrapper's required distribution URL is a synchronized mirror of its catalog pin. Official metadata sources are recorded in [`docs/DEPENDENCY_SOURCES.md`](docs/DEPENDENCY_SOURCES.md).

```bash
# Default includes newer alpha/beta/RC/preview/canary/milestone/EAP releases (not nightlies/snapshots).
python3 scripts/update_versions.py

# Explicitly restrict the refresh to stable releases.
python3 scripts/update_versions.py --stable-only

# Check without modifying the catalog.
python3 scripts/update_versions.py --check
```

The updater aborts without writing if it cannot verify every mapped version against official Gradle/Maven metadata. Renovate is configured not to ignore unstable releases.

### Local release signing

Create a release keystore outside the repository and set these environment variables before running `:app:assembleRelease` or `:app:bundleRelease`:

```bash
export MARBLE_SIGNING_STORE_FILE="/secure/path/marbledo-release.jks"
export MARBLE_SIGNING_STORE_PASSWORD="…"
export MARBLE_SIGNING_KEY_ALIAS="marbledo"
export MARBLE_SIGNING_KEY_PASSWORD="…"
./gradlew :app:assembleRelease :app:bundleRelease
```

Never commit the keystore or passwords. The `.gitignore` excludes common keystore and APK/AAB files.

### CI and GitHub releases

- `.github/workflows/build.yml` builds, lints, and tests on pushes and pull requests.
- `.github/workflows/weekly-dependency-update.yml` runs weekly or manually, refreshes catalog versions from official metadata, validates the result, and opens/updates a pull request. `stable_only` is off by default.
- `.github/workflows/release.yml` runs for pushed `v*` tags or manually. For a manual run, choose the source ref and enter a version such as `1.2.3` or `v1.2.3`; the workflow normalizes it to a `v`-prefixed tag, creates that tag at the selected commit if it is missing, and never moves an existing tag. It then builds signed APK/AAB files, calculates SHA-256 checksums, and publishes a GitHub Release.

For release automation, configure these repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The Base64 secret must contain the complete keystore bytes. A release intentionally fails when signing secrets are missing.

## Backups and optional Drive setup

Backups can be exported through Android's Storage Access Framework to a user-selected local destination. The export dialog offers either an unencrypted archive or an encrypted archive. Encrypted files use PBKDF2-HMAC-SHA256 to derive an AES-256-GCM key; keep the passphrase safe because it cannot be recovered. Imports can be merged with the current task list; newer task revisions win on ID conflicts. Automatic snapshots are kept in the app's private storage (up to seven recent snapshots) and are deleted if the app is uninstalled or its data is cleared.

**Google Drive is not wired into this build.** The app has no Google Sign-In, Drive scope, cloud token, or Firebase dependency, so creating a Cloud project alone will not enable Drive sync. A future Drive connector would require a Google Cloud project, the Drive API, an Android OAuth client registered for `com.marble098.marbledo` and the release signing certificate's SHA-1 fingerprint, an approved narrow scope such as `drive.file`, and an in-app consent/token flow. Until that connector exists, use local SAF export/import; the core app works without Google Play Services.

## Reminders and troubleshooting

1. **Android 13 and later:** allow notifications in Android's permission prompt and in system app settings. If the permission was denied, open Android Settings → Apps → MarbleDo → Notifications.
2. **Exact alarm access:** for more precise reminders, use MarbleDo's “Enable precise reminders” action or Android Settings → Apps → Special app access → Alarms & reminders. If access is unavailable, MarbleDo falls back to an inexact system alarm; delivery may be delayed.
3. **Battery restrictions:** set MarbleDo to unrestricted/allow background activity if your device aggressively stops apps. Vendor names vary; Xiaomi often uses Autostart, Samsung uses Background usage limits, and Huawei/Oppo use App launch or Battery management.
4. **Notification channels:** check that the “Task reminders” channel is enabled and has a suitable importance level. Android lets users override channel settings.
5. **Do Not Disturb:** DND, Focus modes, and manufacturer sound policies may silence reminders even when the app is functioning.
6. **After changing time, time zone, or rebooting:** the boot/time-change receiver recalculates task alarms. Open the app once if the manufacturer has prevented background startup.
7. **Speech input:** the microphone action uses an installed Android speech-recognition service. Availability, language support, and any network use are controlled by that provider; typed quick-add remains fully local.

## Known scope limits

- The included Persian holiday asset covers a fixed set of national/official dates; it is not a live official calendar feed. Religious/lunar dates are calculated with Android ICU and can differ by a day from local authority announcements; the ±2-day adjustment is provided for that reason.
- Calendar-category preferences control both the local event categories shown and which bundled holiday categories may trigger the daily occasion notification. The fixed holiday asset is not a live feed; remote updates and a Drive connector are not included.
- Exact delivery depends on Android permissions and vendor battery policy. Without exact-alarm access, Android may defer reminders.
- The repository was assembled in an environment without Java, Gradle, `adb`, or Android SDK. **A local Android build/test was not run here**, so compile/runtime verification must be completed by the included CI workflow or on a machine with the requirements above.

---

# ماربل‌دو | MarbleDo

**قدم‌های کوچک، لحظه‌های ارزشمند.**

ماربل‌دو یک برنامهٔ آفلاین‌محور برای مدیریت کارها و شمارش معکوس است. رابط کاربری به‌صورت پیش‌فرض فارسی و راست‌چین است و می‌توان آن را به انگلیسی و چپ‌چین تغییر داد. کارها، یادآورها، تقویم و ترجیحات روی دستگاه می‌مانند، مگر این‌که کاربر خودش پشتیبان صادر کند.

## امکانات پیاده‌سازی‌شده

- **کارها:** افزودن هوشمند فارسی/انگلیسی، راهنمای صوتی مرحله‌به‌مرحله برای دسته، عنوان، تاریخ شمسی، ساعت و اولویت، جست‌وجو، فیلتر، سنجاق، مرتب‌سازی، ساخت دسته‌بندی، اولویت، چک‌لیست، برچسب، بایگانی، تکرار و یادآوری.
- **تقویم:** نمایش تاریخ شمسی، میلادی و قمری، نمای ماه/برنامه/سال، شروع هفته از شنبه، اصلاح قمری قابل‌تنظیم و اعلان روزانهٔ مناسبت‌های محلی.
- **شمارش معکوس:** ساخت سریع با زمان‌های آمادهٔ یک‌ساعته/روزانه/هفتگی/ماهانه/سالانه یا تاریخ‌وساعت دقیق، ورود و نمایش تاریخ شمسی/میلادی/قمری، ۱۲ تم، نمای تمرکز تمام‌صفحه و ویجت Glance.
- **تنظیمات:** فارسی/انگلیسی و RTL/LTR، رقم فارسی/لاتین/عربی، فونت متغیر وزیرمتن با وزن متناسب برای عنوان/متن/برچسب، تم روشن/تیره/AMOLED/پویا، اندازهٔ نوشته، کاهش حرکت و ترجیحات تقویم.
- **ذخیره‌سازی:** Room با Migration نسخهٔ ۱ به ۲ و ۳، نگهداری سنجاق و دستهٔ کار، Proto DataStore، پشتیبان خودکار با WorkManager و وارد/خارج‌کردن پشتیبان محلی از SAF با رمزگذاری اختیاری AES-GCM.
- **حریم خصوصی:** بدون Firebase، تحلیل‌گر، تبلیغ یا مجوز اینترنت در برنامه. سرویس تشخیص گفتار اندروید ممکن است بر اساس تنظیمات دستگاه از ارائه‌دهندهٔ بیرونی استفاده کند.
- **معماری و کیفیت:** Kotlin/Compose، ماژول‌های feature/core، Koin، Navigation 3، R8 نسخهٔ انتشار، StrictMode در debug، تست واحد، ماژول Baseline Profile/Macrobenchmark و گردش‌کارهای CI، به‌روزرسانی وابستگی و انتشار امضاشده.

## ساخت و آزمون

### پیش‌نیازها

- JDK 17.
- Android SDK Platform **37.2** و Build Tools **37.0.0** (مقادیر فعلی کاتالوگ) و پذیرش مجوز SDK.
- لینوکس/مک: `curl`، `unzip` و `sha256sum`. ویندوز: `curl.exe` و PowerShell.

اسکریپت `gradlew` نسخهٔ درج‌شده در `gradle/wrapper/gradle-wrapper.properties` را از سرویس رسمی Gradle دریافت، SHA-256 رسمی را بررسی و سپس اجرا می‌کند. این URL آینهٔ نسخهٔ `gradle` در کاتالوگ است و updater آن را همگام می‌کند؛ فایل باینری Wrapper JAR در مخزن قرار نگرفته است.

```bash
./gradlew :app:assembleDebug
./gradlew :core:domain:test :core:data:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest :feature:tasks:testDebugUnitTest \
  :feature:calendar:testDebugUnitTest :feature:countdown:testDebugUnitTest \
  :app:testDebugUnitTest :app:lintDebug
```

APK آزمایشی در `app/build/outputs/apk/debug/` ساخته می‌شود. مسیر خروجی schema روم `core/data/schemas/` است.

### کاتالوگ نسخه‌ها

نسخهٔ پلاگین‌ها، وابستگی‌ها و بسته‌های Android SDK در `gradle/libs.versions.toml` نگهداری می‌شود؛ URL لازم Gradle Wrapper آینهٔ همگام‌شدهٔ نسخهٔ کاتالوگ است. منابع رسمی در [`docs/DEPENDENCY_SOURCES.md`](docs/DEPENDENCY_SOURCES.md) آمده‌اند.

```bash
# حالت پیش‌فرض: نسخهٔ منتشرشدهٔ آلفا/بتا/RC/پیش‌نمایش/Canary/Milestone/EAP هم مجاز است؛ snapshot/nightly کنار گذاشته می‌شود.
python3 scripts/update_versions.py

# فقط نسخه‌های پایدار.
python3 scripts/update_versions.py --stable-only

# فقط بررسی؛ بدون تغییر فایل.
python3 scripts/update_versions.py --check
```

اگر metadata رسمی قابل دریافت یا تجزیه نباشد، اسکریپت هیچ به‌روزرسانی ناقصی نمی‌نویسد. Renovate نیز برای نادیده‌نگرفتن نسخه‌های ناپایدار تنظیم شده است.

### امضای نسخهٔ محلی

یک keystore بسازید و مسیر/مقادیر آن را پیش از اجرای release تنظیم کنید:

```bash
export MARBLE_SIGNING_STORE_FILE="/secure/path/marbledo-release.jks"
export MARBLE_SIGNING_STORE_PASSWORD="…"
export MARBLE_SIGNING_KEY_ALIAS="marbledo"
export MARBLE_SIGNING_KEY_PASSWORD="…"
./gradlew :app:assembleRelease :app:bundleRelease
```

keystore یا رمزها را commit نکنید؛ `.gitignore` فایل‌های keystore و APK/AAB را نادیده می‌گیرد.

### CI و انتشار گیت‌هاب

- `build.yml` روی push و pull request، همهٔ ماژول‌ها را build، lint و test می‌کند.
- `weekly-dependency-update.yml` هفتگی یا دستی نسخه‌ها را از metadata رسمی به‌روز می‌کند، Build/Test را می‌گذراند و pull request می‌سازد. ورودی `stable_only` پیش‌فرض خاموش است.
- `release.yml` با push تگ `v*` یا اجرای دستی فعال می‌شود. در اجرای دستی، ref مبدأ را انتخاب و نسخه‌ای مثل `1.2.3` یا `v1.2.3` وارد کنید؛ workflow آن را به تگ `v`دار تبدیل می‌کند و اگر تگ وجود نداشته باشد، آن را روی commit انتخاب‌شده می‌سازد (تگ موجود را جابه‌جا نمی‌کند). سپس APK/AAB امضاشده و checksum می‌سازد و GitHub Release منتشر می‌کند.

برای انتشار خودکار این Secrets را در GitHub Actions ثبت کنید: `ANDROID_KEYSTORE_BASE64`، `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS` و `ANDROID_KEY_PASSWORD`. secret اول باید کل فایل keystore را به Base64 داشته باشد. بدون این secrets انتشار عمداً fail می‌شود.

## پشتیبان و راه‌اندازی اختیاری Drive

پشتیبان را می‌توان از طریق Storage Access Framework به مقصد محلی انتخاب‌شده صادر کرد. پنجرهٔ خروجی دو حالتِ بدون رمز و رمزگذاری‌شده دارد. نسخهٔ رمزگذاری‌شده از PBKDF2-HMAC-SHA256 برای ساخت کلید AES-256-GCM استفاده می‌کند؛ گذرواژه را نگه دارید، چون بازیابی نمی‌شود. فایل‌های واردشده با کارهای فعلی ادغام می‌شوند و در تداخل شناسه، نسخهٔ جدیدتر اولویت دارد. حداکثر هفت پشتیبان خودکار در حافظهٔ خصوصی برنامه نگهداری می‌شود و با حذف برنامه یا پاک‌کردن داده‌هایش از بین می‌رود.

**اتصال Google Drive در این نسخه پیاده‌سازی/فعال نشده است.** برنامه ورود Google، scope درایو، token ابری یا Firebase ندارد؛ ساخت پروژهٔ Cloud به‌تنهایی همگام‌سازی را فعال نمی‌کند. اتصال آینده به پروژهٔ Google Cloud، فعال‌کردن Drive API، OAuth Client اندروید برای `com.marble098.marbledo` و SHA-1 گواهی انتشار، scope محدود مانند `drive.file` و جریان رضایت/توکن در خود برنامه نیاز دارد. تا آن زمان از پشتیبان محلی SAF استفاده کنید؛ هستهٔ برنامه بدون Google Play Services کار می‌کند.

## عیب‌یابی اعلان‌ها

۱. **Android 13 به بعد:** مجوز اعلان را هنگام درخواست برنامه و در تنظیمات سیستم روشن کنید: تنظیمات ← برنامه‌ها ← MarbleDo ← اعلان‌ها.
۲. **آلارم دقیق:** برای یادآوری دقیق‌تر، از دکمهٔ «فعال‌سازی یادآوری دقیق» یا دسترسی ویژهٔ «Alarms & reminders» استفاده کنید. در نبود مجوز، آلارم جایگزین غیردقیق است و ممکن است دیر برسد.
۳. **محدودیت باتری:** اجرای پس‌زمینه/شروع خودکار را برای ماربل‌دو مجاز کنید. نام منوها بسته به سازنده فرق دارد؛ شیائومی معمولاً Autostart، سامسونگ Background usage limits و هواوی/اوپو App launch یا Battery management دارد.
۴. **کانال اعلان:** کانال «یادآوری کارها» را روشن و اهمیت آن را بررسی کنید؛ اندروید اجازه می‌دهد کاربر کانال‌ها را جداگانه تغییر دهد.
۵. **مزاحم نشوید:** DND، Focus Mode و تنظیمات صوتی سازنده ممکن است اعلان را بی‌صدا کنند.
۶. **پس از تغییر ساعت، منطقهٔ زمانی یا راه‌اندازی مجدد:** گیرندهٔ بوت/تغییر ساعت آلارم کارها را دوباره زمان‌بندی می‌کند. اگر سازنده اجرای پس‌زمینه را بسته، برنامه را یک بار باز کنید.
۷. **ورودی صوتی:** دکمهٔ میکروفون به سرویس تشخیص گفتار نصب‌شده متکی است. دسترس‌پذیری، زبان و احتمال استفاده از شبکه را همان ارائه‌دهنده تعیین می‌کند؛ افزودن سریع متنی محلی است.

## محدودیت‌های شناخته‌شده

- فایل تعطیلات فارسی فقط چند تاریخ ثابت ملی/رسمی را پوشش می‌دهد و خوراک زندهٔ رسمی نیست. ترجیحات دستهٔ تقویم مشخص می‌کنند کدام مناسبت‌های موجود نمایش داده شوند و اجازهٔ اعلان روزانه داشته باشند. تاریخ‌های مذهبی/قمری با ICU اندروید محاسبه می‌شوند و ممکن است با اعلام مرجع محلی یک روز اختلاف داشته باشند؛ به همین دلیل اصلاح ±۲ روز وجود دارد.
- به‌روزرسانی آنلاین مناسبت‌ها و اتصال Drive پیاده‌سازی نشده است.
- دقت اعلان به مجوز اندروید و سیاست باتری سازنده بستگی دارد؛ در نبود مجوز آلارم دقیق، اندروید ممکن است اعلان را عقب بیندازد.
- محیط این مخزن JDK، Gradle، `adb` و Android SDK قابل‌استفاده نداشت؛ **Build/Test محلی در این نوبت اجرا نشد**. تأیید کامپایل و اجرا باید در CI یا محیط دارای پیش‌نیازها انجام شود.
