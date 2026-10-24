<div dir="rtl">

<p align="center">
  <img src="docs/brand/logo.png" alt="لوگوی موج" width="120">
</p>

<h1 align="center">موج / Mowj</h1>

<p align="center"><b>یک پخش‌کنندهٔ سبک و زیبا برای آهنگ‌هایی که همین الان روی گوشی‌ات داری.</b><br>
متن آهنگ کلمه‌به‌کلمه، ۹ سبک نوار پخش، پس‌زمینهٔ زنده و طراحی‌شده با تمرکز روی مصرف کم باتری و رم.</p>

<div dir="ltr" align="center">

![License: MIT](https://img.shields.io/badge/license-MIT-green) ![Release v0.3.0](https://img.shields.io/badge/release-v0.3.0-blue) ![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-brightgreen) ![Java only](https://img.shields.io/badge/code-Java-orange)

</div>

> نسخهٔ انگلیسی: [README.md](README.md) - تغییرات: [CHANGELOG.md](CHANGELOG.md) - دانلود: [Releases](https://github.com/mhrsdev/music-player-mowj/releases)

«موج» (Mowj) فقط فایل‌های موسیقی روی خود گوشی را پخش می‌کند. بدون حساب کاربری، بدون تبلیغ، بدون استریم، بدون آنالیتیکس.
پکیج `com.mhrsdev.musicplayer`، حداقل اندروید ۸٫۰ (minSdk 26)، target/compile SDK 37. فقط Java (بدون Kotlin)، layoutهای XML و Viewهای سفارشی، MVVM، Media3، و SQLite خود اندروید (بدون Room).

> **اول این را بخوان.** بیشتر چیزها با تست‌های JVM و رندرهای Robolectric بررسی شده‌اند، و چند مورد (نصب، کارت اعلان) روی گوشی خود سازنده. دکمه‌های هدست، انتخاب فایل متن آهنگ، Android Auto، ویجت‌ها روی لانچرهای دیگر و اعداد مصرف باتری و رم **هنوز روی گوشی واقعی تأیید نشده‌اند.** بخش [محدودیت‌های شناخته‌شده](#محدودیتهای-شناختهشده) را ببین.

## تصاویر

این تصاویر را خود کد برنامه روی داده‌های نمونه ساخته است (Robolectric، با جلدهای واقعی و مشخصات آهنگ‌های دارای مجوز آزاد؛ [`docs/CREDITS.md`](docs/CREDITS.md))، یعنی عکس از دستگاه نیستند و فونت و محوشدگی روی گوشی ممکن است کمی فرق کند. متن زیر عکس‌ها انگلیسی است چون نمونه‌ها به همین زبان رندر شده‌اند.

<div dir="ltr">

<table>
  <tr>
    <td align="center" valign="top"><img src="docs/screens/g1-home-dark.png" width="220" alt="Home"><br>Home</td>
    <td align="center" valign="top"><img src="docs/screens/u2-library-hub-dark.png" width="220" alt="Library"><br>Library</td>
    <td align="center" valign="top"><img src="docs/screens/l1-word3-dark.png" width="220" alt="Word-synced lyrics"><br>Word-synced lyrics</td>
    <td align="center" valign="top"><img src="docs/screens/b2-picker-dark.png" width="220" alt="Seek bar styles"><br>Seek bar styles</td>
  </tr>
  <tr>
    <td align="center" valign="top"><img src="docs/screens/q1-performance-dark.png" width="220" alt="Performance settings"><br>Performance</td>
    <td align="center" valign="top"><img src="docs/screens/o1-headset-two-dark.png" width="220" alt="Headset and outputs"><br>Headset &amp; outputs</td>
    <td align="center" valign="top"><img src="docs/screens/s1-widget-dark.png" width="220" alt="Home-screen widgets"><br>Widgets</td>
    <td align="center" valign="top"><img src="docs/screens/g1-covers-sheet.png" width="220" alt="Generated covers"><br>Generated covers</td>
  </tr>
</table>

</div>

رندرهای بیشتر (تم روشن، فارسی و RTL، تایمر خواب، صف پخش، پشتیبان‌گیری، صفحه‌های به‌روزرسانی) در [`docs/screens/`](docs/screens/) هستند.

## امکانات

**کتابخانه و پخش**
- اسکن تدریجی MediaStore در SQLite؛ آهنگ‌ها، آلبوم‌ها، خواننده‌ها، پلی‌لیست‌ها، علاقه‌مندی‌ها، تاریخچه، تعداد پخش، مرتب‌سازی، انتخاب چندتایی، مشخصات آهنگ / اشتراک‌گذاری / حذف. دکمهٔ اسکن دوباره و انتخاب پوشهٔ موسیقی در تنظیمات هست.
- صفحهٔ اصلی بر پایهٔ کتابخانه و تاریخچهٔ گوش دادن خودت (پیشنهاد سریع، تازه‌اضافه‌شده‌ها، قدیمی‌ها، میکس‌ها، دهه‌ها، سبک‌ها) و جستجو بین آهنگ، آلبوم، خواننده و پلی‌لیست.
- پلی‌لیست (ساخت، تغییر نام، حذف، افزودن و برداشتن آهنگ)، صف پخش (پخش بعدی، افزودن، جابه‌جایی با کشیدن، حذف با کشیدن به کنار همراه با بازگردانی، شافل با بازگردانی، تکرار خاموش / همه / یکی)، بازیابی صف و جای پخش بعد از راه‌اندازی دوباره، تایمر خواب (چند دقیقه با کم‌شدن تدریجی صدا، یا پایان آهنگ).
- Media3 ExoPlayer در یک سرویس پیش‌زمینه (`MediaSessionService`): اعلان و کنترل روی صفحهٔ قفل، audio focus، مکث با درآوردن هدفون.
- **صدا**: اکولایزر، بیس، virtualizer، loudness، سرعت پخش، رد کردن سکوت، ReplayGain (فقط صدا را کم می‌کند، هرگز زیاد نمی‌کند).

**ظاهر و حس برنامه**
- **۹ سبک نوار پخش** با انتخابگر و پیش‌نمایش زنده: Wavy، Waveform، Bars، Ribbon، Pulse dots، LED ladder، Mountain، Thread and beads، Liquid. سبک‌های مبتنی بر بلندی صدا از بلندی واقعی همان آهنگ استفاده می‌کنند که روی خود گوشی استخراج و ذخیره می‌شود.
- **متن آهنگ همگام کلمه‌به‌کلمه** با پر شدن هر کلمه، محوشدگی ملایمی که با فاصله از خط در حال خوانده‌شدن بیشتر می‌شود، پرش با لمس، تنظیم تأخیر برای هر آهنگ و اندازهٔ متن. خط‌هایی که زمان‌بندی کلمه ندارند، با تخمین کلمه‌به‌کلمه نمایش داده می‌شوند. جزئیات: [`docs/LYRICS_AND_BACKDROP.md`](docs/LYRICS_AND_BACKDROP.md).
- **پس‌زمینهٔ زنده**: رنگ‌های جلد آهسته پشت پخش‌کننده و متن آهنگ جابه‌جا می‌شوند و صفحهٔ اصلی و سرِ صفحهٔ آلبوم / خواننده / پلی‌لیست را هم رنگ می‌کنند.
- **جلد ساخته‌شده** برای آهنگ‌های بدون جلد: طرح شب‌مانند قطعی (هر بار و روی هر گوشی یکسان)، سازگار با فارسی، در برنامه، اعلان و ویجت‌ها. [`docs/GENERATED_COVERS.md`](docs/GENERATED_COVERS.md).
- **تم‌ها**: سیستم / روشن / تیره / مشکی، رنگ گرفتن اختیاری از جلد، و **Material You (رنگ پویا)** در اندروید ۱۲ به بالا (پیش‌فرض خاموش است تا هویت موج حفظ شود). حرکت فنری و دکمهٔ پخش / مکث که شکلش تغییر می‌کند. [`docs/THEMING.md`](docs/THEMING.md).
- **تمام‌صفحه (edge-to-edge)** با رعایت ناچ و ناوبری ژستی یا سه‌دکمه‌ای، و حالت اختیاری پخش‌کنندهٔ immersive.
- **فارسی (RTL) و انگلیسی**، فونت وزیرمتن برای فارسی و Google Sans Flex برای لاتین. زبان از تنظیمات عوض می‌شود.

**منبع‌های متن آهنگ** (اولین نتیجه برنده است؛ نتیجه‌ها و پیدا نشدن‌ها ذخیره می‌شوند):
1. فایل کنار آهنگ با همان نام: `.lrc`، `.elrc`، `.json` (و `.ttml` و `.txt`).
2. تگ‌های داخل فایل: ID3 `USLT` / `SYLT`، `LYRICS` در FLAC / Vorbis، `©lyr` در MP4.
3. [LRCLIB](https://lrclib.net) (رایگان، بدون کلید؛ قابل خاموش کردن).

تگ‌هایی که فقط اسم یک سایت یا تبلیغ دارند نادیده گرفته می‌شوند. اگر چیزی پیدا نشود، از صفحهٔ خالی می‌توانی **دستی جستجو کنی** یا **یک فایل متن اضافه کنی**؛ و دکمهٔ متن برای آهنگ‌های بدون متن نمایش داده نمی‌شود.

**کارایی (تنظیمات > Performance)**
- حالت‌های آماده: **Full** (پیش‌فرض)، **Balanced** (بدون محوشدگی متن، پس‌زمینهٔ ثابت، تا ۶۰ فریم) و **Light** (نوار پخش هم ثابت، بدون جهش، تا ۳۰ فریم).
- کلیدهای جداگانه: انتقال بین صفحه‌ها، دکمه‌های فنری، پس‌زمینهٔ متحرک، محوشدگی متن، متن کلمه‌به‌کلمه، نوار پخش متحرک، و سقف نرخ فریم انیمیشن‌ها (هم‌اندازهٔ صفحه / ۶۰ / ۳۰). تغییرها همان لحظه اعمال می‌شوند. «کاهش حرکت» و حالت بهینه‌سازی باتری اندروید هم افکت‌های تزئینی را خاموش می‌کنند.

**هدست و خروجی‌ها (تنظیمات > Headset)**
- **یک بار / دو بار / سه بار / نگه داشتن** دکمهٔ پخش-مکث هندزفری و هدست سیم‌دار را به کار دلخواه نگاشت کن (پیش‌فرض: پخش-مکث، بعدی، قبلی، هیچ).
- ادامه‌دادن وقتی هدست وصل می‌شود، مکث وقتی قطع می‌شود، ادامه بعد از تماس تلفنی.
- **دستگاه‌های خروجی**: خروجی‌های وصل‌شده را فهرست می‌کند، فعال را نشان می‌دهد، صدایش را نشان می‌دهد، تعویض‌کنندهٔ خروجی سیستم را باز می‌کند و برای هر دستگاه یک **پروفایل صدای ذخیره‌شده** (اکولایزر، بیس، سرعت) نگه می‌دارد که وقتی همان دستگاه پخش می‌کند اعمال می‌شود.
- فهرست تست دستی: [`docs/HEADSET_CHECKLIST.md`](docs/HEADSET_CHECKLIST.md).

**سیستم**
- سه **ویجت صفحهٔ اصلی** (جمع‌وجور، مربعی، پهن).
- فهرست مرور **Android Auto** (تازه‌اضافه‌شده، پرپخش‌ترین، علاقه‌مندی‌ها، پلی‌لیست‌ها، آلبوم‌ها، خواننده‌ها) و ادامهٔ پخش.
- **پشتیبان‌گیری و بازیابی** پلی‌لیست‌ها، علاقه‌مندی‌ها و تاریخچه در فایلی که خودت انتخاب می‌کنی (آهنگ‌ها با تگ‌هایشان دوباره پیدا می‌شوند).
- **به‌روزرسانی خودکار از GitHub Releases**: حداکثر روزی یک بار وقتی برنامه باز است، به‌علاوهٔ دکمهٔ دستی؛ هرگز در پس‌زمینه. قبل از باز شدن نصب‌کنندهٔ سیستم، فایل APK با **SHA-256** و **گواهی امضا** بررسی می‌شود و نصب را خودت تأیید می‌کنی.

**ساخته‌شده برای مصرف کم باتری و رم**
- بدون WorkManager، آلارم، گیرندهٔ بوت، wake lock اضافه یا polling در پس‌زمینه. سرویس حدود ۶۰ ثانیه بعد از توقف پخش خودش را می‌بندد.
- انیمیشن‌ها از یک frame driver مشترک عبور می‌کنند و فقط وقتی دیده می‌شوند و موسیقی پخش می‌شود کار می‌کنند. بلور زنده نداریم: محوشدگی یک بار روی یک بیت‌مپ کوچک محاسبه می‌شود.
- شبکه فقط برای جستجوی اختیاری متن آهنگ (وقتی صفحهٔ متن باز است) و بررسی به‌روزرسانی استفاده می‌شود.
- سقف‌ها با یک تست اسکن کد اجرا می‌شوند: [`docs/PERF_BUDGET.md`](docs/PERF_BUDGET.md). ادعاها همراه با شاهد در کد و روش اندازه‌گیری روی گوشی: [`docs/BATTERY_AUDIT.md`](docs/BATTERY_AUDIT.md). حجم APK نسخهٔ release حدود ۳ مگابایت است.

## نصب

1. فایل `MusicPlayer-vX.Y.Z.apk` (و اگر خواستی `.sha256`) را از [صفحهٔ Releases](https://github.com/mhrsdev/music-player-mowj/releases) دانلود کن.
2. بررسی اختیاری: `sha256sum -c MusicPlayer-vX.Y.Z.apk.sha256`.
3. APK را روی گوشی باز کن. وقتی اندروید پرسید، برای مرورگر یا فایل‌منیجرت اجازهٔ **نصب برنامه‌های ناشناس (Install unknown apps)** را بده.
4. موج را باز کن و دسترسی به موسیقی (و در اندروید ۱۳ به بالا اعلان‌ها) را بده.

نسخه‌ها از `v0.2.0` به بعد با کلید انتشار سازنده امضا شده‌اند، پس نسخهٔ جدید روی قبلی نصب می‌شود و داده‌هایت می‌ماند. `v0.1.0` با کلید debug امضا شده بود؛ رفتن از آن به نسخهٔ بعدی یک بار حذف برنامه لازم دارد (علاقه‌مندی‌ها و پلی‌لیست‌ها از بین می‌روند). بعد از آن موج می‌تواند از تنظیمات خودش را به‌روز کند.

## ساخت از سورس

نیازمندی‌ها: JDK 17 یا بالاتر (با 21 ساخته شده)، Android SDK همراه با `platforms;android-37.0` و یک `build-tools` تازه.

<div dir="ltr">

```bash
export ANDROID_HOME=/opt/android-sdk        # or sdk.dir in local.properties (gitignored)
./gradlew :app:assembleDebug test lint      # what CI runs on every push
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

</div>

ابزارها: Gradle wrapper نسخهٔ **9.0.0**، Android Gradle Plugin نسخهٔ **8.13.2** (جدیدترینی که روی Gradle 9.0.0 اجرا می‌شود)، سطح سورس Java 17، compileSdk و targetSdk برابر 37 و minSdk برابر 26. نسخهٔ وابستگی‌ها در `gradle/libs.versions.toml` است.

<details><summary>نصب Android SDK روی یک لینوکس خالی</summary>

<div dir="ltr">

```bash
mkdir -p /opt/android-sdk/cmdline-tools && cd /tmp
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools /opt/android-sdk/cmdline-tools/latest
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/opt/android-sdk \
  "platform-tools" "platforms;android-37.0" "build-tools;35.0.0"
```

</div>
</details>

### انتشار و امضا

انتشار با `.github/workflows/release.yml` انجام می‌شود: یک تگ `vX.Y.Z` بفرست، یا workflow را دستی اجرا کن (GitHub > Actions > *Release* > *Run workflow*، شاخهٔ `main`، نسخهٔ `X.Y.Z`). این workflow تست‌های واحد و `assembleRelease` را اجرا می‌کند و بعد یک GitHub Release با عنوان «Mowj vX.Y.Z» می‌سازد، همراه با APK، فایل `.sha256` و بخش مربوط در `CHANGELOG.md`. نسخه‌ای که پسوند `-` ندارد انتشار عادی (stable و «latest») است؛ مثلاً `v1.0.0-rc.1` pre-release می‌شود. `v0.1.0` و `v0.2.0` پیش‌انتشار بودند و از `v0.3.0` به بعد انتشارها عادی‌اند.

keystore انتشار **داخل مخزن نیست** و فقط در secretهای GitHub Actions است (`ANDROID_KEYSTORE_BASE64`، `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS`، `ANDROID_KEY_PASSWORD`). بدون آن‌ها، ساخت محلی یا روی fork با کلید debug اندروید امضا می‌شود که برای آزمایش خوب است ولی روی نسخهٔ release نصب نمی‌شود. به‌روزرسانی‌کنندهٔ داخل برنامه APKی را که گواهی امضایش با برنامهٔ نصب‌شده فرق دارد قبول نمی‌کند.

نسخه از `-PappVersionName` و `-PappVersionCode` می‌آید (workflow آن‌ها را تنظیم می‌کند).

## معماری

یک ماژول `:app`، بسته‌بندی بر اساس لایه. قرارداد بین UI و بقیه: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

<div dir="ltr">

```
 ui/*  (Activity, Fragments, custom Views)        View       - ViewBinding, no business logic
   |  observes LiveData / calls intents
 ui/*ViewModel                                    ViewModel  - LiveData only, no Context / View refs
   |
 domain/repository (interfaces) + domain/model (records)
   ^                       ^
 data/*                 playback/*
 SQLite, scanner,       Media3 ExoPlayer + MediaSessionService,
 lyrics, waveform,      queue store, sleep timer, audio effects,
 settings, backup,      headset controls
 update
 di/AppContainer  - hand-written DI (no Hilt/Koin), cheap to construct
```

</div>

- `ui` هرگز `data.*` یا `playback.*` را import نمی‌کند؛ فقط interfaceهای `domain` را می‌بیند که `di/AppContainer` به آن می‌دهد.
- تمام SQL در `data/db/query/*Queries` است و نام جدول و ستون‌ها در `data/db/contract/*Contract`. بدون Room، بدون Kotlin، بدون coroutines / RxJava.
- تصویرها را یک بارگذار کوچک خودمان با `LruCache` محدود لود می‌کند (کتابخانهٔ تصویر نداریم).

## تست

<div dir="ltr">

```bash
./gradlew test      # JVM unit tests (JUnit 4 + Robolectric): data, lyrics, playback logic, ViewModels, widgets, perf budget, renders
./gradlew lint
```

</div>

CI (`.github/workflows/android.yml`) روی هر push دستور `assembleDebug test lint` را اجرا می‌کند. هنوز تست روی دستگاه (instrumented) نداریم.

## مجوزها

<div dir="ltr">

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (up to Android 12L) | read your music from MediaStore |
| `POST_NOTIFICATIONS` | playback notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | keep music playing in the background |
| `WAKE_LOCK` | CPU awake only while a local file plays (ExoPlayer) |
| `BLUETOOTH_CONNECT` (Android 12+) | show the name of the connected Bluetooth output |
| `INTERNET`, `ACCESS_NETWORK_STATE` | optional LRCLIB lyrics lookup and the GitHub update check |
| `REQUEST_INSTALL_PACKAGES` | hand a downloaded update to the system installer (you confirm it) |

</div>

## حریم خصوصی

بدون آنالیتیکس، تبلیغ، حساب کاربری، گزارش خرابی یا ردیابی. کتابخانه، علاقه‌مندی‌ها، پلی‌لیست‌ها و تاریخچه روی دستگاه می‌مانند (`allowBackup` خاموش است؛ اگر نسخهٔ پشتیبان می‌خواهی از پشتیبان‌گیری داخل برنامه استفاده کن). دو نوع درخواست شبکه داریم که هر دو فقط در صورت نیاز انجام می‌شوند:
- جستجوی متن آهنگ: وقتی چیزی به‌صورت محلی پیدا نشود و متن آنلاین روشن باشد، **عنوان، خواننده، آلبوم و مدت** آهنگ به `lrclib.net` فرستاده می‌شود (تنظیمات > Lyrics).
- بررسی به‌روزرسانی: آخرین نسخه را از API مربوط به GitHub Releases می‌پرسد، حداکثر روزی یک بار وقتی برنامه باز است.

## محدودیت‌های شناخته‌شده

- **روی گوشی واقعی تأیید نشده**: دکمه‌های هدست و هندزفری (منطق برنامه تست شده، ولی رسیدن کلید از گوشی به برنامه نه)، انتخاب فایل متن آهنگ، Android Auto، ویجت‌ها روی لانچرهای دیگر، آیکن تم‌پذیر لانچر، رنگ پویا روی والپیپرهای واقعی، مصرف باتری و رم، و هزینهٔ محوشدگی روی GPUهای ضعیف. ظاهر برنامه فقط از رندرهای Robolectric دیده شده است.
- **دو دستگاه بلوتوث همزمان**: اندروید API عمومی برای این کار ندارد. اگر گوشی‌ات Dual Audio / Audio Sharing دارد، موج به تنظیماتش لینک می‌دهد؛ وگرنه فقط روی یک خروجی پخش می‌کند.
- **مدیریت‌کننده‌های سخت‌گیر باتری** (شیائومی، اوپو، ریلمی، «sleeping apps» سامسونگ) ممکن است برنامه را فریز یا بکشند و دکمهٔ هدست دیگر آن را بیدار نکند. روی این گوشی‌ها بهینه‌سازی باتری را برای موج خاموش کن.
- کارت رسانهٔ سیستم در بعضی گوشی‌ها (One UI، اندروید ۱۳ به بعد) نوار پخش خودش را از روی media session می‌کشد و برنامه‌ها نمی‌توانند ظاهرش را عوض کنند.
- متن فارسی هنوز توسط یک فارسی‌زبان بومی بازبینی نشده است.
- در دسترس بودن LRCLIB دست ما نیست، و زمان‌بندی کلمه فقط وقتی هست که فایل یا ورودی LRCLIB آن را داشته باشد (وگرنه تخمین زده می‌شود).
- استریم یا کاتالوگ آنلاین نداریم: فقط فایل‌های محلی. ویرایشگر تگ و crossfade هنوز نیست.

## مشارکت / قواعد پروژه

فقط Java (`git ls-files | grep -E '\.kts?$'` نباید چیزی چاپ کند؛ فایل‌های Gradle با Groovy هستند)، MVVM، بدون Room، بدون کلاس `final`، Java 17، Gradle 9.0.0. مصرف باتری و رم الزام سخت‌اند (`docs/PERF_BUDGET.md`). اول `CLAUDE.md`، `docs/STATUS.md` و `docs/ARCHITECTURE.md` را بخوان. روی یک شاخه کار کن؛ `main` وضعیت تحویل‌شده است.

## مجوز

تحت [مجوز MIT](LICENSE) منتشر شده است - Copyright (c) 2026 mhrsdev.

فونت‌های همراه (Google Sans Flex و Vazirmatn) تحت SIL Open Font License 1.1 هستند؛ به
[`app/src/main/assets/licenses/FONTS-NOTICE.txt`](app/src/main/assets/licenses/FONTS-NOTICE.txt) نگاه کن. جلدها و مشخصات نمونه در تصاویر: [`docs/CREDITS.md`](docs/CREDITS.md).

</div>
