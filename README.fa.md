<div dir="rtl">

# Music Player (اندروید)

<div dir="ltr">

![License: MIT](https://img.shields.io/badge/license-MIT-green) ![Pre-release v0.1.0](https://img.shields.io/badge/release-v0.1.0%20pre--release-orange)

</div>

یک **پخش‌کنندهٔ موسیقی محلی**، سریع و کم‌مصرف برای اندروید، با هویت بصری اختصاصی، متن آهنگ همگام (synced lyrics)، سه سبک seek bar و پشتیبانی کامل از فارسی و RTL.

> **وضعیت: پیش‌انتشار (pre-release) `v0.1.0`.** پروژه build می‌شود و unit testها پاس می‌شوند، اما تا الان فقط با تست‌های JVM و رندرهای Robolectric بررسی شده است؛
> **روی گوشی واقعی یا emulator هنوز تأیید نشده است.** بخش [محدودیت‌های شناخته‌شده](#محدودیتهای-شناختهشده) را ببینید.
> نسخهٔ انگلیسی: [README.md](README.md) - تغییرات: [CHANGELOG.md](CHANGELOG.md)

- پکیج `com.mhrsdev.musicplayer`، minSdk 26 (اندروید ۸٫۰)، target/compile SDK 37
- **فقط Java** (بدون Kotlin)، layoutهای XML + Viewهای سفارشی، MVVM، SQLite خود فریم‌ورک (بدون Room)، Media3

## صفحه‌ها

رندرهای واقعی که از خود کد برنامه روی داده‌های نمونه تولید شده‌اند (Robolectric، پوشهٔ `docs/screens/`؛ نه از روی دستگاه):

<div dir="ltr">

| Home | Home (فارسی، RTL) | Home (light) |
|---|---|---|
| ![Home](docs/screens/p3-home-dark.png) | ![Home fa](docs/screens/p3-home-fa-dark.png) | ![Home light](docs/screens/p3-home-light.png) |

| Library | Artists | Search |
|---|---|---|
| ![Library](docs/screens/p3-library-dark.png) | ![Artists](docs/screens/p3-artists-dark.png) | ![Search](docs/screens/p3-search-dark.png) |

| Player dark | Player light | Widget gallery |
|---|---|---|
| ![Player dark](docs/screens/p2-player-dark.png) | ![Player light](docs/screens/p2-player-light.png) | ![Widgets dark](docs/screens/t2-widget-gallery-dark.png) |

</div>

دو رندر Player **بدون آهنگ بارگذاری‌شده** هستند (عنوان خالی، دکمه‌های غیرفعال)؛ به همین دلیل نمای lyrics نشان داده نشده است. برای پلیر کامل، mockupهای زیر را ببینید.

طرح هدف (mockup مرورگری در `docs/design/`؛ پیاده‌سازی از آن پیروی می‌کند ولی پیکسل‌به‌پیکسل یکسان نیست):

<div dir="ltr">

![Design overview](docs/design/png/00-overview.png)

| Player | Lyrics | Queue | RTL player |
|---|---|---|---|
| ![Player](docs/design/png/05-player-cover.png) | ![Lyrics](docs/design/png/06-player-lyrics.png) | ![Queue](docs/design/png/07-queue.png) | ![RTL player](docs/design/png/11-rtl-player.png) |

</div>

## امکانات

پیاده‌سازی‌شده (کد موجود است و در جاهایی که ذکر شده با unit test پوشش داده شده):

- **کتابخانه (Library)** - اسکن افزایشی MediaStore به SQLite (در اولین اجرا هم بلافاصله پس از دادن مجوز)؛ آهنگ‌ها، آلبوم‌ها، هنرمندان، پلی‌لیست‌ها، لایک‌ها، تاریخچه و تعداد پخش. صفحهٔ اصلی Library با کولاژ محو لایک‌ها، شبکهٔ ۲×۲ و skeleton هنگام بارگذاری.
- **صفحهٔ Home** (hero carousel، skeleton loading) بر پایهٔ کتابخانه و تاریخچهٔ پخش خودتان (quick picks، آلبوم‌ها، پلی‌لیست‌ها - مرتب‌شده بر اساس پخش‌های شما).
- **جستجو** در آهنگ، آلبوم، هنرمند و پلی‌لیست با فیلتر.
- **پخش** - Media3 ExoPlayer داخل یک foreground `MediaSessionService`: نوتیفیکیشن، کنترل lock screen / بلوتوث / هدست، audio focus، توقف با جداشدن هدفون.
- **افزودن به پلی‌لیست** - از منوی آهنگ و منوی پلیر: انتخاب پلی‌لیست موجود یا ساخت پلی‌لیست جدید.
- **صف (Queue)** - پخش بعدی، افزودن به صف، جابه‌جایی با drag، حذف با swipe و Undo، shuffle با Undo، تکرار خاموش / همه / یکی، بازیابی صف و موقعیت پس از راه‌اندازی مجدد.
- **Sleep timer** - N دقیقه (با fade-out) یا پایان آهنگ؛ مقدار پیش‌فرض قابل تنظیم است.
- **متن آهنگ (Lyrics)** - تگ‌های embedded (ID3 / FLAC / MP4)، فایل کناری `.lrc` / `.txt`، سپس جستجوی آنلاینِ اختیاری در [LRCLIB](https://lrclib.net) (کلید در Settings). خطوط همگام هایلایت می‌شوند و با لمس seek می‌کنند؛ نتایج (و نتیجهٔ «پیدا نشد») cache می‌شوند. هنگام آفلاین بودن، چیپ Retry نشان داده می‌شود.
- **سبک‌های seek bar** (در Settings): *Wavy* (پیش‌فرض)، *Waveform* و *Bars*؛ دادهٔ waveform پوشش بلندی واقعی صداست که روی خود دستگاه و به‌صورت lazy استخراج و cache می‌شود.
- **پلیر** - پلیر روشن و تیره با کنترل کنتراست روی accent مشتق از کاور، چیپ مسیر خروجی (This phone / بلوتوث / ...) و انیمیشن «پرواز کاور» بین mini-player و پلیر کامل.
- **مجوز نوتیفیکیشن** در اولین پخش درخواست می‌شود (اندروید ۱۳+).
- **تم‌ها** - سیستم / روشن / تیره / مشکی، رنگ‌گیری اختیاری از کاور، accent با کنترل کنتراست.
- **فارسی و RTL** - ترجمهٔ کامل `fa`، فونت Vazirmatn برای فارسی (انتخاب فونت بر اساس خط، پس متن مخلوط فارسی/لاتین فونت درست می‌گیرد)، layout آینه‌ای؛ seek barها و دکمه‌های پخش LTR می‌مانند. زبان در Settings قابل تغییر است.
- **Battery saver** - تنظیم (Auto / On / Off) که حرکت، blur و grain را خاموش می‌کند؛ تنظیم reduce-motion سیستم را هم رعایت می‌کند.

هنوز نیست (برنامه‌ریزی‌شده، در v0.1.0 نیست): equalizer، ویجت صفحهٔ اصلی، Android Auto، UI تغییر نام / حذف پلی‌لیست و حذف آهنگ از آن، دکمهٔ rescan، انتخاب پوشهٔ موزیک (ردیف UI فقط نمایشی است)، ویرایش تگ، crossfade، کاتالوگ آنلاین.

## نصب

1. فایل `MusicPlayer-v0.1.0.apk` (و در صورت تمایل `.sha256`) را از [صفحهٔ Releases](https://github.com/mhrsdev/music-player/releases) دانلود کنید.
2. بررسی: `sha256sum -c MusicPlayer-v0.1.0.apk.sha256`
3. APK را روی گوشی باز کنید و برای مرورگر / فایل‌منیجر اجازهٔ «نصب برنامه‌های ناشناس» بدهید.

**نکتهٔ امضا.** تا زمانی که نگه‌دارندهٔ پروژه release keystore تنظیم نکرده، APKهای release با کلید استاندارد **debug** اندروید امضا می‌شوند. نصب می‌شوند، اما (الف) امضای سطح فروشگاه ندارند و (ب) برای آپدیت روی APKای که با کلید دیگری امضا شده باید برنامهٔ قبلی را اول uninstall کرد (داده‌ها مثل لایک‌ها، پلی‌لیست‌ها و تاریخچه از بین می‌رود).

## ساخت از سورس

نیازمندی‌ها: JDK 17+ (با 21 ساخته شده)، Android SDK با `platforms;android-37.0` و `build-tools;35.0.0`.

<div dir="ltr">

```bash
export ANDROID_HOME=/opt/android-sdk        # or sdk.dir in local.properties (gitignored)
./gradlew :app:assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease              # R8 + resource shrinking
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

</div>

نسخه و امضا با Gradle property یا متغیر محیطی تنظیم می‌شوند:

<div dir="ltr">

| Name | Meaning | Default |
|---|---|---|
| `-PappVersionName` | `versionName` | `0.1.0` |
| `-PappVersionCode` | `versionCode` | `1` |
| `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | release signing | unset: debug keystore |

| Toolchain | Version |
|---|---|
| Gradle wrapper | **9.0.0** |
| Android Gradle Plugin | **8.13.2** (AGP 9.x needs Gradle >= 9.1) |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |

</div>

مراحل نصب Android SDK روی لینوکس خام در [README.md](README.md) (بخش «Installing the Android SDK on a bare Linux box») آمده است.

### انتشار (Release)
دو راه برای ساخت release وجود دارد که هر دو را `.github/workflows/release.yml` انجام می‌دهد:

1. push کردن یک tag به شکل `vX.Y.Z`.
2. اجرای دستی: GitHub > Actions > *Release* > *Run workflow*، انتخاب branch (معمولاً `main`) و وارد کردن نسخه (مثلاً `0.1.0`). GitHub خودش tag با نام `v<version>` را روی آخرین commit آن branch می‌سازد.

workflow ابتدا unit testها و `assembleRelease` را اجرا می‌کند و سپس یک GitHub Release شامل `MusicPlayer-vX.Y.Z.apk`، فایل `.sha256` و بخش مربوط از `CHANGELOG.md`. tagهایی که با `v0.` شروع شوند یا `-` داشته باشند pre-release علامت می‌خورند. برای امضای واقعی secretهای اختیاری مخزن: `ANDROID_KEYSTORE_BASE64`، `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS`، `ANDROID_KEY_PASSWORD`.

## تست‌ها

<div dir="ltr">

```bash
./gradlew test      # JVM unit tests (JUnit 4 + Robolectric)
./gradlew lint
```

</div>

CI (`.github/workflows/android.yml`) روی هر push دستور `assembleDebug test lint` را اجرا می‌کند. هنوز تست روی دستگاه (instrumented) وجود ندارد.

## معماری

یک ماژول `:app` با ساختار package-by-layer. جزئیات و قراردادها: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

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
 lyrics, waveform,      queue store, sleep timer
 settings
 di/AppContainer  - hand-written DI (no Hilt/Koin), cheap to construct
```

</div>

- لایهٔ `ui` هرگز `data.*` / `playback.*` را import نمی‌کند؛ فقط interfaceهای `domain` را می‌بیند که `di/AppContainer` می‌دهد.
- **SQLite فریم‌ورک** (`SQLiteOpenHelper`): همهٔ SQL در `data/db/query/*Queries` و نام جدول/ستون در `data/db/contract/*Contract` است.
- **بدون Room، بدون Kotlin، بدون coroutine/RxJava**: یک thread به نام `ioExecutor` (بعد از ۳۰ ثانیه بیکاری تمام می‌شود)؛ نتایج به‌صورت `LiveData` می‌رسند.
- **Media3** `MediaSessionService` تنها `ExoPlayer` داخل process را نگه می‌دارد و UI از طریق `PlayerController` آن را کنترل می‌کند.
- کاور آهنگ‌ها با یک bitmap loader کوچک و `LruCache` محدود بارگذاری می‌شود (بدون کتابخانهٔ تصویر).

### طراحی برای باتری و RAM
- wake lock اختصاصی نداریم؛ فقط wake mode محلی ExoPlayer هنگام پخش. سرویس حدود ۶۰ ثانیه پس از توقف پخش متوقف می‌شود.
- به‌روزرسانی موقعیت فقط هنگام پخش و دیده‌شدن UI؛ observerها به lifecycle وابسته‌اند؛ هیچ polling یا sync پس‌زمینه‌ای نیست.
- انیمیشن‌ها (wavy، bars، blur، grain) وقتی برنامه یا پلیر دیده نمی‌شود متوقف و با battery saver / reduce motion خاموش می‌شوند.
- اسکن کتابخانه افزایشی است؛ استخراج waveform lazy و نمونه‌برداری پراکنده است، cache می‌شود و در battery saver رد می‌شود.
- شبکه فقط برای LRCLIB، در صورت نیاز و برای هر آهنگ، با cache (حتی برای «پیدا نشد») و هرگز در پس‌زمینه.
- کاورهای downsample‌شده با cache چندمگابایتی؛ thread مربوط به `ioExecutor` در بیکاری تمام می‌شود.

## مجوزها (Permissions)

<div dir="ltr">

| Permission | Why |
|---|---|
| `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (<= Android 12L, `maxSdkVersion=32`) | read your music from MediaStore |
| `POST_NOTIFICATIONS` | playback notification (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | keep music playing in the background |
| `WAKE_LOCK` | CPU stays awake only while a local file is playing (ExoPlayer) |
| `INTERNET` | **only** for the optional LRCLIB lyrics lookup (can be switched off in Settings) |

</div>

## حریم خصوصی

بدون analytics، بدون تبلیغات، بدون حساب کاربری، بدون crash reporting، بدون ردیابی. کتابخانه، لایک‌ها، پلی‌لیست‌ها و تاریخچه روی دستگاه می‌مانند (`allowBackup` خاموش است). تنها درخواست شبکهٔ برنامه جستجوی اختیاری LRCLIB است: وقتی متن آهنگ به‌صورت محلی پیدا نشود و گزینهٔ «Find lyrics online» روشن باشد، **عنوان، هنرمند، آلبوم و مدت** آهنگ به `lrclib.net` فرستاده می‌شود. با خاموش کردن آن در Settings > Lyrics برنامه کاملاً آفلاین کار می‌کند.

## ساختار پروژه

<div dir="ltr">

```
app/src/main/java/com/mhrsdev/musicplayer/
  MusicPlayerApp, MainActivity     application + single-activity shell
  di/                              AppContainer and per-layer modules
  domain/{model,repository}/       records + interfaces the UI codes against
  data/{db,scanner,repository,lyrics,waveform,settings}/
  playback/                        ExoPlayerController, PlaybackService, queue store, sleep timer
  ui/{theme,widget,nav,home,search,library,player,queue,settings}/
  fake/                            in-memory implementations used by tests and render previews
app/src/main/res/                  layouts, drawables, fonts, values (en) + values-fa
app/src/test/                      JVM unit tests
docs/                              ARCHITECTURE, DESIGN, STATUS, screenshots, design mockups
.github/workflows/                 android.yml (CI), release.yml (tag -> GitHub Release)
```

</div>

## مشارکت / قوانین پروژه

فقط Java (خروجی `git ls-files | grep -E '\.kts?$'` باید خالی باشد؛ فایل‌های Gradle با Groovy هستند)، MVVM، بدون Room (SQLite فریم‌ورک، SQL فقط در کلاس‌های query)، پکیج‌بندی تمیز، Gradle 9.0.0. مصرف باتری و RAM الزام سخت‌گیرانه است. اول `CLAUDE.md`، `docs/STATUS.md` و `docs/ARCHITECTURE.md` را بخوانید. روی branch جدا کار کنید؛ `main` وضعیت تحویلی است.

## محدودیت‌های شناخته‌شده

- **روی دستگاه واقعی تأیید نشده**: اسکن MediaStore روی کتابخانهٔ واقعی، نوتیفیکیشن / lock screen / بلوتوث، شروع foreground service در اندروید ۱۴+، استخراج waveform با MediaCodec و مصرف باتری. ظاهر فقط از راه رندرهای Robolectric بررسی شده است.
- APK نسخهٔ release با کلید debug امضا شده (بخش «نصب»)؛ آپدیت خودکار ندارد.
- متن‌های فارسی را فارسی‌زبان بومی بازبینی نکرده است.
- در دسترس بودن LRCLIB دست ما نیست (هنگام توسعه گاهی 503 داده است).
- UI تغییر نام / حذف پلی‌لیست و حذف آهنگ از آن، دکمهٔ rescan و انتخاب پوشه هنوز وجود ندارد (ردیف پوشه‌ها در Settings فقط نمایشی است).
- کاتالوگ آنلاین یا streaming وجود ندارد: برنامه فقط فایل‌های روی دستگاه را پخش می‌کند.

## نقشهٔ راه (Roadmap)

برنامه‌ریزی‌شده، هنوز پیاده‌سازی نشده: equalizer، ویجت صفحهٔ اصلی، Android Auto، ویرایش پلی‌لیست در UI (تغییر نام / حذف)، دکمهٔ rescan، انتخاب پوشهٔ موزیک، layout تبلت / landscape، crossfade، ویرایش تگ، release با keystore واقعی، مجموعه تست روی دستگاه، audit باتری.

## مجوز (License)

منتشرشده با [مجوز MIT](LICENSE) - Copyright (c) 2026 mhrsdev.

فونت‌های داخل پروژه (Google Sans Flex و Vazirmatn) تحت مجوز SIL Open Font License 1.1 هستند؛ به
[`app/src/main/assets/licenses/FONTS-NOTICE.txt`](app/src/main/assets/licenses/FONTS-NOTICE.txt) مراجعه کنید.

</div>
