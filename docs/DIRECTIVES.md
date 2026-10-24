# Owner Directives Archive (append-only)

The owner writes in Persian. Each entry: original text verbatim, then an English summary.

---

## D1 — 2026-10-23 — Project kickoff

**Original (verbatim):**

> میخوام یه موزیک پلیر اندرو بسازم
> تو قرار وظیفه یه ناظر و مدیر داشته باشی و قرار از ساب اجنتات برای تولید استفاده بکنی. تو فقط قرار نظارت بکنی و دستورات منو انجام بدی و به گوش اونا برسونی و وظیفه کد نویسی به عهده ساب اجنتات هستش. و همینطور وظیفه دارید در حین کار همیشه یه لیستی از تسکات و تسکای مونده و تموم شده داشته باشی و همینطور متنهایی که من بهت میدم و به صورت یه آرشیو توی اونجا دستوراتی که بهت میدم و داشته باشی و خلاصه شون رو نگه داری که اگر یه زمان چت رو عوض کردم بدون نیاز به توضیح اضافه راحت بتونه اون ایجنت بعدیم متوجه بشه که کجای کار هستیم و بایستی همیشه این وضعیت آپدیت بمونه.
> این نمونه یو آی مد نظرم هستش، ولی چیزی که من ازت میخوام از این قرار بهتر باشه و یو آی رو میسپری به کار کلاود اوپس ۵.۵ و بقیه کارها هم با سونت ۵.۵.
>
> و اینکه برنچ مین میشه برنچ اصلیمون، تو روی اون مرجش میکنی و برنچ دیگه ای اگر داری اون برای حالا خودت و ساب اجنتات. من چیزی که میخوام خروجی کار وقتی کار تمام میشه بعد مرج بشه رو مین.

(Six reference screenshots attached; saved in `docs/design-reference/`.)

**Summary:**
1. Build an **Android music player app**.
2. The top-level agent is **supervisor/manager only**: relays the owner's orders to sub-agents, monitors; **sub-agents write all the code**.
3. Always maintain a **task list (todo / in-progress / done)** and an **archive of the owner's instructions with summaries**, kept up to date so a new chat/agent can resume with zero extra explanation → `docs/STATUS.md` + this file + `CLAUDE.md`.
4. The attached screenshots are the UI **reference**; the result must be **better than the reference**.
5. **UI work → Claude Opus 5.5; everything else → Sonnet 5.5.**
6. `main` is the main branch; the supervisor merges into it. Any other branch is for the supervisor and sub-agents while working. The owner wants the **final output merged on `main`**.

**Reference screenshots (what they show):**
| File | Screen |
|---|---|
| 01-now-playing-lyrics | Full player with synced, centered lyrics (active line bold), top pill toggle group (shuffle / repeat-one / queue), lyrics-toggle icon (left), heart (right), title + artist, **wavy** seek slider with elapsed / `-remaining`, prev / pause (wide pill) / next |
| 02-now-playing-queue | Compact player (title, artist, prev / **scalloped "cookie" play-pause** / next, wavy seek) over a **queue bottom sheet**: sleep-timer moon icon, "Playing from artist · N min left" chip, overflow menu, draggable rows (art, title, artist, duration, drag handle), "Queue shuffled — Undo" snackbar |
| 03-home | Home: "New releases" hero carousel (big rounded cards), "Throwbacks" carousel, "Quick picks" list; **floating pill bottom nav** (Home / Search / Library / Settings / profile avatar; selected item expands with label) |
| 04-search | Search field with clear, filter chips (Songs / Albums / Artists / Playlists / Videos), results rows with art, title, explicit "E" badge, artist, overflow menu |
| 05-library | Library: big "Liked songs" card with play button, tiles for Albums / Playlists / Artists / History (with counts), "Recently saved" row |
| 06-artists | Artists grid, circular avatars with names |

Visual language = **Material 3 Expressive**, dark theme, dynamic blurred artwork backdrop on player, rounded shapes, wavy progress, shape-morphing buttons.

---

## D2 — 2026-10-23 — Design skill + visual target

**Original (verbatim):**

> (attached design-atelier.zip) برای دیزاین از این اسکیل استفاده کن
> آره و اون حالا یو آی که بهت دادم عکسشو، اون نمونه یو آی که حالا میخوام اون چیزی که میسازی شباهت داشته باشه به این، ولی میخوام قشنگ تر باشه.

**Summary:**
1. Use the attached **design-atelier** skill for all design work → installed at `.claude/skills/design-atelier/`.
2. The built UI must be **similar to the reference screenshots, but more beautiful** (same structure/language, higher polish — not a different design).

---

## D3 — 2026-10-23 — Second reference (Melodiha) + design deliverable in chat

**Original (verbatim):**

> (2 screenshots attached) این مثلا ملودیغای هستش
> ببین چه دیزاین باحالی داره
> میخوام مال من معرکه باشه
> دیزاین انجام شد عکسهاشو تو چت برام بزار

**Summary:**
1. Screenshots are of the app **"Melodiha"** (Persian music app): media notification in the shade (artwork-tinted deep-red card, **waveform-style progress** with thumb, round prev/pause/next buttons, device name) and the lock-screen media pill (waveform at the bottom edge). Saved as `docs/design-reference/07-*.jpg`, `08-*.jpg`. Persian/RTL is first-class.
2. Owner finds this design very cool and wants **ours to be outstanding ("معرکه")**.
3. **Do the design phase now and show the design images in the chat** (owner reviews visually before implementation).

---

## D4 — 2026-10-23 — Design approved; start implementation; add voice-memo bar seekbar

**Original (verbatim):**

> دیزاین تایید شد، شروع کن پیاده‌سازی رو
> ولی یدونه به اون نوارا اضافه شه که شبیه نوت های موسیقی این نوارا هستن وقتی ویس میگیری با شدت صداته اونمدلی باشه

**Summary:**
1. Design in `docs/design/` is **approved**. Start implementation (T2–T9).
2. Add **one more seek-bar style** next to Wavy and Waveform: vertical **bars whose heights follow the song's actual loudness**, like the voice-message/voice-recording waveform ("Bars"). Played part highlighted, tap/drag to seek. Needs real amplitude extraction per track (cached).
3. The owner did not answer the 5 open design questions; supervisor applied the recommended defaults (see STATUS → Decisions K5–K9). Owner can change them anytime.

---

## D5 — 2026-10-23 — Stop parallel agents; one sub-agent per section; be ready for chat switch

**Original (verbatim):**

> ساب هارو متوقف کن هر بخش فقط یه ساب
> و اماده باش هر لحظه چتو عوض کنم

**Summary:**
1. **Stop all running sub-agents** (done: all 5 wave-1 agents killed).
2. From now on **one sub-agent per section, and only ONE sub-agent running at a time** (sequential, no parallel fan-out). Interpreted conservatively; owner can relax it.
3. Keep `docs/STATUS.md` always resume-ready: the owner may switch chats at any moment, and the next agent must continue without explanation.

---

## D6 — 2026-10-23 — Handoff to a new chat + friend's suggestions (Java, MVVM, battery, less "Google-look")

**Original (verbatim):**

> همه چیزو پوش کن و یه پرامپت بده میخوام برم یه چت دیگه
> و دوستم پیشنهاد کرد بگم
> با جاوا باشه
> معماریشم mvvm
> نمیدونم چرا
> ولی خب گفت بگم
> و اینکه مصرف باتریش خیلی بهینه باشه
> و کمتر شبیه دیزاین کامپوننت گوگل باشن

**Summary:**
1. Push everything; produce a **handoff prompt** for a new chat (see `docs/HANDOFF_PROMPT.md`).
2. Owner relays, without a reason ("don't know why, but he said to say it"): **use Java**; architecture **MVVM**.
3. **Very battery-efficient** playback/app.
4. UI components should look **less like stock Google (Material) components** — a more own, distinctive identity.

**Supervisor analysis (needs owner confirmation of #2a):**
- **MVVM**: compatible with the plan (ViewModel + StateFlow/UDF over the existing domain interfaces). Adopted.
- **Java**: the approved design/implementation is **Jetpack Compose, which is Kotlin-only**. Java means rewriting the UI layer with XML Views/custom Views (custom drawn views are fine for wavy/bars seekbars) and losing Compose/M3-Expressive APIs; Media3/Room/Koin-or-Hilt all work from Java. Java brings **no battery or performance benefit** (battery is decided by wakelocks, tick rates, animation/scan scheduling — not language). Recommendation: **keep Kotlin** (Android's first-class language; Java interop is full). Decision is the owner's: **OPEN QUESTION Q-J — must be asked first in the new chat.** Until answered, no further UI code is written.
- **Battery**: adopted as a hard requirement (see docs/ARCHITECTURE.md → "Battery & efficiency").
- **Less Google-look**: adopted; requires a design revision (task T2b, Opus) of DESIGN.md before more UI code: custom-drawn controls/shapes/typography/motion with own identity (closer to Melodiha), M3 used only as an invisible foundation (accessibility/semantics), no stock-looking M3 widgets.

---

## D7 — 2026-10-23 — Answer to Q-J: Java only, MVVM, no Room, tidy queries, clean structure, Gradle 9.0.0

**Question asked (supervisor):** project language — Kotlin + MVVM (recommended; keeps approved Compose UI) vs Java + MVVM (UI rewritten with XML/custom Views, no battery benefit).

**Original answer (verbatim):**

> الان میگم
> قوانین زیر را رعایت کن 
> استفاده از kt ممنوع است
> از معماری mvvm کمک بگیر
> از room کمک نگیر سیع کن از دیتابیس خود اندروید کمک بگیری
> کوعری ها رو مرتب بزن 
> کد های تمیز با پوشه بندی درست 
> از گردل 9.0.0 کمک بگیر

**Summary (hard rules):**
1. **No Kotlin at all** — every source file is Java (`.java`). Build scripts must be Groovy `.gradle` (no `.kts`) so no `.kt/.kts` exists in the repo. Resolves Q-J: **Java**.
2. Architecture **MVVM** (ViewModel + LiveData/observable state, Repository layer).
3. **No Room.** Use Android's built-in database: `SQLiteOpenHelper` / `SQLiteDatabase` (framework SQLite).
4. "کوعری ها رو مرتب بزن" — read by the supervisor as **"keep the (database) queries tidy"** (typo for "کوئری"): all SQL lives in dedicated query/DAO classes with named constants, no SQL scattered across the code. (Assumption — owner can correct.)
5. **Clean code with proper packaging/folder structure** (package-by-layer + feature sub-packages, small focused classes).
6. **Gradle 9.0.0** (wrapper). Supervisor note: AGP/Gradle compatibility must be verified by the build agent; if Gradle 9.0.0 cannot work with any available AGP, report to owner before deviating.

**Consequences (supervisor):** the Compose/Kotlin scaffold (T1), the approved Compose-based UI plan, and the Kotlin WIP branches (`task/t2-ui-foundation`, `task/t5-data`, `task/t7-lyrics`, `task/tw-waveform`) are obsolete as code (design PNGs/spec stay as the visual target; UI becomes XML/custom Views). Re-scaffold in Java (T1b). Wavy / Waveform / Bars seek bars become custom `View`s.

---

## D8 — 2026-10-23 — Repeat of D7 rules + UI quality bar + parallel sub-agents + ponytail skill

**Original (verbatim):**

> قوانین زیر را رعایت کن 
> استفاده از kt ممنوع است
> از معماری mvvm کمک بگیر
> از room کمک نگیر سیع کن از دیتابیس خود اندروید کمک بگیری
> کوعری ها رو مرتب بزن 
> کد های تمیز با پوشه بندی درست 
> از گردل 9.0.0 کمک بگیر
> ببین، خواستم اینه که یو آی خیلی قشنگ و خوشگل داشته باشه، کمتر شبیه دیزاین های گوگل باشه و دیزاین انحصاری خودمون باشه. تو مایه های همینی که فرستادی، ولی میمقدار قشنگتر، سیغل خورده تر، مصرف باتری و رم کاملا باید بهینه باشه، خیلی کم و مناسب. بعد بقیهش هم دیگه با خودت.
> و از همزمان چند ساب ایجنت استفاده کن و بجز حالا ساب ایجنت یو آی برای بقیه ساب ایجنت ها از اسکیل پونی تیل استفاده کن
> https://github.com/DietrichGebert/ponytail

**Summary:**
1. The D7 rules are repeated (Java only / MVVM / no Room, framework SQLite / tidy queries / clean packaging / Gradle 9.0.0) — they are binding (already in CLAUDE.md "Hard code rules").
2. **UI quality bar:** very beautiful, **less Google-like, an exclusive identity of our own**, in the spirit of the mockups already shown but a bit prettier and more polished ("سیقل‌خورده‌تر"). **Battery AND RAM must be fully optimized — very low.** The rest is left to the supervisor's judgment.
3. **Use several sub-agents in parallel** — this **replaces D5's one-at-a-time rule** (parallel work uses separate git worktrees/branches).
4. **All sub-agents except the UI sub-agent must use the "ponytail" skill** (https://github.com/DietrichGebert/ponytail — "lazy senior dev": smallest complete change, no speculative abstraction). Installed (reviewed, MIT, no hooks) at `.claude/skills/ponytail`, `ponytail-review`, `ponytail-audit`. Supervisor note: where ponytail's minimalism conflicts with D7 (clean packaging, tidy query classes, MVVM layering) the D7 structure rules win.

---

## D9 — 2026-10-23 — Design must be god-tier, battery stays reasonable

**Original (verbatim):**

> ببین به اوپس بگو دیزیان باید خدا باشه ولی مصرف باتری مناسب بمونه
> بهترین UI/UX رو میخوام

**Summary:** Tell the UI agent (Opus) the design must be outstanding ("god-tier") while battery use stays reasonable. Owner wants the best possible UI/UX. Relayed to T2b; balance = beautiful where cheap, documented low-power fallbacks.

---

## D10 — 2026-10-23 — Give the designer the ui-ux-pro-max skill

**Original (verbatim):**

> این اسکیلم به دیزاینر بده
> https://github.com/nextlevelbuilder/ui-ux-pro-max-skill

**Summary:** Give the UI/design sub-agent (Opus) the `ui-ux-pro-max` skill. Installed (reviewed: local CSV data + pure-Python search script, no network/exec of external code; MIT) at `.claude/skills/ui-ux-pro-max`. Run it as `python3 .claude/skills/ui-ux-pro-max/scripts/search.py "<query>" --design-system|--domain ux|motion|typography|color ...` (no `-I`). It is web-leaning; its output is advice only — design-atelier + owner directives (D2, D6–D9) win.

---

## D11 — 2026-10-23 — Five parallel sub-agents, design screenshots in chat, go faster

**Original (verbatim):**

> همزمان 5 تا ساب ایجنت رو استفاده کن و اینکه یو آی هم هر وقت که حالا دیزاین جدیدش کامل شدهش اسکرین شاتاشو همین تو این چت برام بذار. و اینکه پروژه رو سریعتر پیش ببر و آمادش بکن، یه پروژه خیلی خفن میخوام بشه. عجله ای از بابت زمان نداریم، ولی هرچی کارمون تو تایم کمتری انجام بشه خب باز بهتره.

**Summary:**
1. Run **up to 5 sub-agents at the same time** (each in its own git worktree + `task/<id>-<slug>` branch).
2. As soon as the new design is complete, **post its screenshots in this chat** (SendUserFile).
3. Move faster and get the project ready; the owner wants a very impressive ("خفن") result. No hard deadline, but less elapsed time is better.

---

## D12 — 2026-10-23 — One-hour budget, up to 8 sub-agents

**Original (verbatim):**

> خب تو ۱ ساعت زمان داری
> میتونی از حداکثر ۸ ساب ایجنت استفاده کنی

**Summary:** Supervisor has **~1 hour** for the whole project and may run **up to 8 sub-agents in parallel**. Plan compressed: UI split into three parallel Opus agents with strict file ownership (T2 foundation/widgets, T3 screens, T4 player/queue/lyrics) started now on the WIP design v2 (no waiting for approval — owner's design feedback is applied as a follow-up); T8 wiring + T9 QA start as soon as data/playback/lyrics/waveform land; merge to `main` at the end. Honest limit: no device/emulator here, so on-device polish remains a follow-up round.

---

## D13 — 2026-10-23 — Use the previous (v1) design

**Original (verbatim):**

> نه دیزاین قبلی خوب بود همونو استفاده کن

**Summary:** After seeing design v2, the owner says **the previous design (v1, `docs/design/` as approved in D4) was good — use that one.** Supervisor actions: `docs/design/` restored to v1 on the working branch; v2 stays archived only on branch `task/t2b-design-revision` (do not use it as the visual target; the only thing worth reading there is `png/17-player-bars.png` for the Bars seek-bar interaction idea). UI agents T2/T3/T4 were told to retarget to v1. v1 was specced for Compose/M3 Expressive, so implementers must build the **same look as XML + custom Views in Java** (wavy progress, cookie/scalloped morphing play button, floating pill nav, hero carousel, blurred-artwork player, etc. drawn as custom Views) and add the **Bars** seek-bar style (D4) which v1 only describes in text. D6 "less Google-like" is superseded for the visual target by this decision; the battery/RAM rules (no live full-screen blur, draw-phase animation only while visible+playing, Still/low-power fallback) still apply. Design open questions D-A..D-F from v2 are void; v1's defaults K5-K9 stand.

---

## D14 — 2026-10-23 — Release now, README, update main completely; then build the full version

**Original (verbatim):**

> خب بعدش نسخه کاملو بساز + هرچیزی که یه موزیک پلیر لازم داره
> ولی الان ازش یه ریلیز بگیر بزار تو گیت هاب 
> ریدمی و اینهاروهم اکی کن
> پوشه main هم اپدیت کنش کامل

**Summary:**
1. **Now:** cut a **release** of the current state and publish it on **GitHub** (Releases page, installable APK).
2. Get the **README and the related repo docs** in good shape (OK them).
3. **Update `main` completely** (merge the finished, verified work into `main` now, not only at the very end).
4. **Afterwards:** build the **complete version** with **everything a music player needs** (second phase / next release).

**Supervisor notes:** `gh` CLI has an invalid token and the GitHub MCP has no create-release tool, so the Release is created by a **tag-triggered GitHub Actions workflow** (`.github/workflows/release.yml`, uses the built-in `GITHUB_TOKEN`); the APK is also sent to the owner in chat. First release is a **pre-release v0.1.0**: not tested on a real device (no emulator in the sandbox), APK signed with the debug key unless the owner provides a keystore.

---

## D15 — 2026-10-23 — License: MIT

**Original (verbatim):**

> لایسنسو بزار MIT

**Summary:** Put an **MIT license** on the project. Added `LICENSE` (MIT, "Copyright (c) 2026 mhrsdev" — the GitHub account name; owner can change the holder name). README/README.fa must say MIT (R1 told). Bundled fonts keep their own OFL notice (`assets/licenses/FONTS-NOTICE.txt`).

---

## D16 — 2026-10-23 — Name + logo first, then bug fixes, easier UX, more complete, GitHub auto-update

**Original (verbatim):**

> خوبه خوبه ولی اول یه اسم براش انتخاب کن و لوگو
> بعد برو باگهاشو رفع کن راحتی کار باهاش یا همون UI UX رو بهتر کن
> کاملتر کن 
> و یه سیستم اتو اپدیت هم بزار خودکار از گیت هاب اپدیت جدیدشو بگیره

**Summary:**
1. **Pick an app name and a logo** (supervisor chooses). Chosen: **Mowj / موج** ("wave" — ties to the wavy/bars seek bars). Package id `com.mhrsdev.musicplayer` is kept so installs can update in place; only the display name/icon change.
2. Then **fix bugs**, make the app **easier to use** (UI/UX improvements) and **more complete** (features a music player needs).
3. Add an **auto-update system** that automatically fetches the newest release from GitHub (checks GitHub Releases, downloads the APK, verifies, installs). Supervisor design: check only on app foreground ≤ 1×/day + manual button (no background polling → battery), SHA-256 + signing-certificate check, user confirms the system installer.

**Supervisor risk note (owner decision pending):** an APK update only installs over the old app if both are signed with the SAME key. CI currently falls back to a throw-away debug key (different on every runner) → updates would fail. A fixed release keystore is required (owner asked: key as GitHub secrets vs. committed keystore).

---

## D17 — 2026-10-23 — Signing key decision + first real-device bug report

**Signing key (answer to supervisor's question):** "کلید محرمانه توی Secrets (پیشنهاد من)" → the release keystore lives ONLY in GitHub Actions secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`). Supervisor generated the keystore (RSA 4096, alias `mowj`, validity 30 y, SHA-256 cert fingerprint `2E:59:CE:52:…:AA:CD`) and sent the files + instructions to the owner in chat; **nothing secret is committed**. Until the owner adds the secrets, release builds fall back to the debug key (updates then won't install). v0.1.0 was debug-signed → one-time uninstall/reinstall needed when moving to the first release-key build.

**Bug report (owner, verbatim):** "باگ" + a real-device screenshot of the Home screen (Persian/RTL) running v0.1.0. First real-device evidence. Visible bugs: (1) mini-player play button drawn as a distorted irregular polygon; (2) floating nav + mini-player translucent, list content ('Quick picks', captions) shows through/behind and no bottom padding; (3) Home section titles in English ('Recently added', 'Throwbacks', 'Quick picks') in the Persian UI; (4) hero carousel: left neighbour is a squeezed narrow pill; (5) artwork missing (gradient placeholders) for many albums while others load. Routed: (1) U3, (2)(3)(4) U1, (5) E-perf — all as first, separately-pushed fixes.

---

## D18 — 2026-10-23 — More real-device bug reports (player, lock screen, shade, Bluetooth, end of list)

**Original (verbatim, with 4 screenshots: full player, lock screen, notification shade, Home bottom):**

> ببین تو منوی بالا و صفحه قفل چیزی نشون نمیده + قسمتی که زده بلوتوث جاش اسم دستگاه رو بزنه کنارش علامت لوتوث
> این منوهم وقتی تا تهش میری اخرش باگه

(Previous message, same session: "باگ" + Home screenshot; "مارجین کافی نداره" + light-theme mini-player/nav screenshot.)

**Summary / findings:**
1. **No media notification on the notification shade and nothing on the lock screen** while playing (only "Media output" button; no Mowj card). Routed to new agent N1 (suspected root cause: T6's in-process player never starts/binds `PlaybackService`, so no MediaSession notification/foreground service). Needs a Robolectric regression test + device checklist.
2. **Output chip shows only "بلوتوث"** — owner wants the **device name** next to the Bluetooth icon (needs BLUETOOTH_CONNECT runtime permission on API 31+). Routed to U3.
3. **Full-player title is huge and clipped** (only "no et orchestre)" visible, collides with the 3-dot menu). Routed to U3.
4. **Bottom of scrolling screens is buggy** ("when you scroll to the very end"): last items hidden behind/through the translucent dock (nav + mini-player), no bottom padding. Routed to U1 (already queued as fix #1 there).

---

## D19 — 2026-10-23 — Secrets OK; ugly play/stop button; shallow app; lyrics/blur/M3 expressive/Google Sans Flex brief; empty Settings

**Original (verbatim, with screenshots: GitHub secrets page, full player with distorted play button, empty Liked-songs screen with overlapping layout, a lyrics reference screenshot):**

> کلید های گیت هاب اوکی شدن
> این دکمه استپ خیلی یجوریه و قشنگ نیست
> و باگ جدید
> و اینکه برنامه عملا خیلی کوچیکه و تو هیچی برای کشف کردن و اسکرول توش نداری
> زود میرسی ته برنامه
> Lyrics and dynamic background blur generated from the cover art
> و
> Material theming هم نداریم
> ستینگ هم خیلی خالیه و هیچ چیز خاصی نداره
> این پرامپت هم دوستم نوشته:
> Use native material 3 expressive shapes and shape transitions
> Native bounce and motion physics documented by Google
> https://m3.material.io/blog/building-with-m3-expressive
> Use Google sans flex in different weights and rounding values
> Documented here
> https://fonts.google.com/specimen/Google+Sans+Flex?preview.script=Latn
>
> Make sure nothing floating is low in opacity  like the nav bar
> Work on page transitions
> State transitions (play/pause) and adapt a coherent style of "jiggle"
>
> We need support for lyrics
> Embedded or external files, with word for word sync
> And a emulation of word for word sync for lyrics without word by word sync
> Words needs to have dynamic blur and gradients
> ولی من میخوام بگم حواست باشه باعث نشه بهینه سازی های برنامه خراب بشن
> و اینکه مستقیما از اونا استفاده نکن و مشابهشو بسبک برنامه خودمون بساز و اضافه کن

**Summary:**
1. GitHub Actions **secrets are set** (4 signing secrets, repo now `mhrsdev/music-player-mowj`) → release workflow signs with our key; first release signed this way needs one uninstall/reinstall of v0.1.0.
2. The **play/stop button** is ugly/weird (v0.1.0 shows the distorted polygon; U3's geometry fix is pending release) → redesign as a clean, beautiful shape (circle ↔ rounded squircle spring morph), coherent "jiggle" state transitions (agent M1).
3. **New bug:** the empty *Liked songs* screen layout overlaps (big circle icon over the title, hint text over the Play/Shuffle buttons) → U2.
4. The app is **too small/shallow — nothing to discover, reaches the end of Home immediately** → much richer Home/discovery (agent H1).
5. **Lyrics + dynamic cover-art background blur** with **word-by-word sync** (embedded or external files), an **emulated word-by-word sync** for lines lacking word timing, and **dynamic blur + gradient fill on words** (reference: `docs/design-reference/09-lyrics-reference-blur-wordsync.jpg`) → agent L1.
6. **Material theming is missing** (Material You/dynamic color, M3 roles/shape) and **Settings is very empty** → M1 (theme) + U1 (Settings content).
7. Friend's brief (to adopt in OUR OWN style, **not** copied from Google code): M3 Expressive shapes + shape transitions; native-style bounce/spring physics; Google Sans Flex in different weights and **roundness** (ROND axis); nothing floating may be low-opacity (nav bar etc.); page transitions; coherent jiggle for state transitions.
8. **Constraint from the owner:** must **not break the app's optimizations** (battery/RAM), and **don't use Google's implementations directly — build similar ones in our own style**.

---

## D20 — 2026-10-23 — Build, update README, real songs in sample screenshots (+ misc bug reports)

**Original (verbatim):**

> بیلدو بگیر بعدش ریدیمیشو اپدیت کن و تو عکسهای نمونه برنامه بجای فرم اهنگ های حالی چنتا اهنگ دانلود کن قرار بده

**Also in this stretch (verbatim, summarised):** "دکمه پخش خوبه دکمه توقف زشته ولی اگه پیشنهاد بهتری داره میتونه ارائه بده" (play button good, PAUSE button ugly — M1 to propose options, D19); "اگه آهنگ فایل lyric نداره پس نیازی به اینم نیست" (hide the lyrics button when the track has no lyrics → L1); "رفرش کردن لیستش کلا باگ هستش، برای این که یه موزیکی که دانلود کردم رو اضافه کنم ناچار شدم Clear Data کنم" (library refresh broken → F1/F2: rescan on every foreground, MediaStore observer while visible, force rescan, pending/non-music filters).

**Summary:** (1) take the full build, (2) update README/docs, (3) the sample/screenshot data must show **real songs with real covers** instead of the invented empty-looking placeholders → R2 (CC/public-domain tracks + covers, metadata only; audio not committed; debug-only assets; CREDITS.md). Final README screenshot refresh happens after the UI agents finish.

---

## D21 — 2026-10-23 — Next version: headphone/device features; dependency tidy; no `final` classes; Java 17

**Original (verbatim):**

> و برای نسخه بعدی قابلیت کنترل هندزفری و هدفون ایناهم اضافه کن
> و یا شناسایی دیوایسی که باهاش داری اهنگو گوش
> یا بخش همزمان یه اهنگ از دو دیوایس و قابلیت کنترل ولوم و مشخصات جدا برای هر دیوایس(نمایشش درصورتی که چنتا کانکت شده بود)
> activity = "1.13.0"
> fragment = "1.9.1"
> lifecycle = "2.11.0"
> recyclerview = "1.4.0"
> constraintlayout = "2.2.2"
>
> وقتی AppCompat میزنی نیازی نیست بزنی
> clude now removed Final Class Dont Using Final mod
> try to using java 17

**Summary / interpretation:**
1. **Next version (v0.3.0 feature):** (a) headphone/earbud/headset **controls** (button mapping: single/double/triple press, auto-resume on connect, pause on disconnect); (b) **detect the device** the music is playing on; (c) **simultaneous playback to two devices** + **per-device volume and per-device profile** (shown only when several outputs are connected). Supervisor feasibility note: Android has **no public API for playing one app's audio on two Bluetooth devices at once** (only OEM features such as Samsung Dual Audio / Android LE-Audio "audio sharing"); we implement listing all connected outputs, showing the active one, switching output through the system output switcher, per-device volume display/adjust (AudioManager keeps per-device volumes) and **per-device sound profiles** (EQ/bass/etc. keyed by device), and deep-link to the system's dual-audio/audio-sharing settings where the OEM offers it. → agent O1.
2. **Dependencies:** the version-catalog lines `activity 1.13.0, fragment 1.9.1, lifecycle 2.11.0, recyclerview 1.4.0, constraintlayout 2.2.2` — "when you add AppCompat you don't need to add (these)": drop redundant direct declarations that AppCompat/Material already bring transitively (verify with `:app:dependencies` that no version regresses and the build stays green).
3. **Code rule: do NOT use the `final` modifier on classes** (remove existing `final class`; records/enums stay as they are; `static final` constants and `final` fields where semantically needed are fine). (Interpretation of "Final Class Dont Using Final mod".)
4. **Use Java 17** (source/target 17; use its idioms where they simplify: records, `instanceof` pattern matching, switch expressions, text blocks for SQL, `var` sparingly; no APIs above minSdk without desugaring).

---

## D22 — 2026-10-23 — Pause button = option B; backgrounds follow song/content; generated covers; fullscreen; friend's phone dock bug

**Original (verbatim, several short messages):**

> اره ولی دکمه شروع همین دکمه فعلی خوبه ولی استپ همون نمونه که گفتی بکنش
> و اینکه پس زمینه با اهنگ و محتوا تغییر کنه
> تو اپدیت بعدی واسه اهنگایی که جلد ندارن برنامه خودش یدونه بسازه
> (earlier: "تو اپدیت بعدی یادت باشه برنامه فول اسکرین بشه کامل"; "باگ تو بعضی گوشیا — مال من اکیه مال دوستم نه" + screenshot: dock touching the bottom edge, black strip)

**Summary:**
1. **Play button stays as is; PAUSE = M1's option B** (squircle ↔ circle with centred bars); no cookie/scalloped outline.
2. **Backgrounds must change with the song and the content** (player/lyrics, Home glow from focused hero/current track, album/artist/playlist headers from their covers) via a reusable `AmbientBackdropView` (FULL animated only in player; SOFT static elsewhere). → L1 engine, H1, U2.
3. **Songs without a cover get an app-generated one** (deterministic generative art, album-shared, Persian-capable, cached, used in UI + notification/widget) → agent G1.
4. **Fully full-screen (edge-to-edge, cutouts, optional immersive player)** and the **dock/bottom-inset bug on the friend's phone** → U1 (task/u1b-fullscreen).

---

## D23 — 2026-10-23 — Next release must not be a Pre-release

**Original (verbatim):** "تو نسخه بعدی دیگه Pre-release نزن"

**Summary:** From the next release (v0.3.0) on, GitHub Releases are normal (stable, "Latest") releases; pre-release only for versions with a `-suffix`. v0.2.0 was already published as pre-release (owner can ask to flip it). Release title becomes "Mowj vX.Y.Z". Implemented by R1 in `release.yml` (branch `task/r1-stable-release`).

---

## D24 — 2026-10-23/24 — Generated covers quality; notification bar; "create several new bars"

**Original (verbatim, short messages in sequence):**

> جلدی که میسازه مث مال این عکسه خوشگل باشه
> لوگورو جای اینکه از فارسیش الهام بگیری با اینگلیسی ترکیبش تو فارسی کمک بگیر
> با دکمه های هندزفری نمیشه کنترل کرد / توی نسخه بعدی بهینه شه واس دیوایسهای دیگه
> نسبت به 0.1.0 خیلی رو موبایل دوستم لگی تر هستش / بجای کاهش افکت ها بهینه تر کن مصرفو / مصرف های همزمان بیخودو کاهش بده و توی جایی که لازم نیست چیزهای بیخود مصرف نداشته باشن
> لوگوی برنامه هم خوب نیست و بهترش کن
> نوار اینجاش قشنگ نیس / تو ستینگم اپشن انتخاب نوارو اضافه کن  (screenshot: One UI shade media card)
> پس چنتا نوار جدیدم بساز

**Summary:** (1) generated covers must match the reference beauty (G1 done, sheet shown); (2) logo = bilingual Latin M + Persian م (A: M2 recommended, awaiting owner's final pick); (3) headset buttons broken → O1; (4) v0.2.0 laggier than v0.1.0 on friend's phone → **optimise consumption, do NOT remove effects; cut redundant simultaneous work** → E; (5) the One UI media-card seek bar (system-drawn, from the MediaSession) cannot be restyled by apps on Android 13+ — explained to the owner; he answered "then create several NEW bars" → **B2: 5-6 new in-app seek-bar styles + a style picker with live previews in Settings > Playback**.

---

## D25 — 2026-10-24 — Lyrics junk bug; "where do lyrics come from?" (friend's question)

**Original (verbatim):**

> دریافت متنشم هنو خوب نیس  (screenshot: lyrics view showing only "RozMusic.com")
> جواب سوالش؟  (screenshot: friends' group asking "لیریک رو از کجا میاره؟")

**Summary:** (1) Tags containing only a site watermark/advert must not count as lyrics -> L2 (junk filter + query normaliser + empty states with Retry / Add file / Search manually). (2) Owner needed an answer for his friends: lyrics resolve in order sidecar `.lrc`/`.elrc`/`.json` next to the audio -> embedded tags (ID3 USLT/SYLT, FLAC Vorbis, M4A ©lyr) -> LRCLIB (lrclib.net, free, no key), cached in the app DB. Answered in chat.

---

## D26 — 2026-10-24 — Settings: a Performance section to reduce animations

**Original (verbatim):**

> و توی ستینگ یه بخش پرفورمنس بزار بشه توش انیمیشنارو کم کرد تا کمتر لگ شه
> در اپدیت بعدی

**Summary:** For the next release (v0.3.0): Settings gets a **Performance** section where the user can dial animations/effects down for laggy phones. Defaults keep full effects (D24: optimise, don't cut); the section is an opt-in escape hatch. Task Q1 (Opus, `task/q1-performance-settings`).

---

## D27 — 2026-10-24 — Keep all 6 new seek bars

**Original (verbatim):** "همه ۶ تا نوار"

**Summary:** Owner keeps all six new seek-bar styles from B2 (Ribbon, Pulse dots, LED ladder, Mountain, Thread & beads, Liquid) alongside Wavy/Waveform/Bars. B2 merged into ccr.

---

## D28 — 2026-10-24 — After v0.3.0: next release is performance-only

**Original (verbatim):** "بعد این اپدیت شروع میکنی روی کارکردن برای اپدیت بعدی و فقط هدفت بهبود پرفورمنس هستش"

**Summary:** Once v0.3.0 is released and verified, start the next release (v0.4.0) whose ONLY goal is performance (smoothness, startup, memory, battery). No new features. D24 still applies: optimise, do not cut effects; the Performance settings section (Q1) stays as an opt-in escape hatch. Plan in docs/STATUS.md (PF1–PF5).

---

## D29 — 2026-10-24 — URGENT: severe lag + overheating even on a Galaxy S26 Ultra

**Original (verbatim):**

> نه مشکل پرفورنس نسخه فعلی خیلی بده گوشی منکه s26 ultra هستش هم لگ میخوره تو برنامه
> بهترین گوشی سامسونگه
> گوشی ملتم شده بود بخاری

**Summary:** The current build (owner's phone: S26 Ultra, the best Samsung) lags in the app and the phone got very hot ("melted, like a heater") -> sustained CPU/GPU load. This is the top priority and is not limited to v0.4.0: start performance agents NOW (PF-A UI hot paths, PF-B background CPU) and ship the fix as a quick v0.3.1. D24/D28 still apply: optimise, don't cut effects by default.

---

## D30 — 2026-10-24 — Screen recording + "the app crashes a lot in different parts"

**Original (verbatim):** (attached screen recording "Screen_Recording_…One_UI_Home.mp4", 4.8 s, 720x1560@120) + "و برنامه کرش میکنه زیاد رو قسمت های مختلفش"

**Summary:** Owner reports frequent crashes in various parts of the app. The recording shows Home (hero 'تازه‌های کتابخانه', 'ادامه‌ی پخش', 'دوباره سراغشان برو', 'ساخته‌شده برای تو', 'انتخاب‌های سریع'), scrolled down and back up, then the app window closes straight to the One UI launcher at ~2.4 s. No stack trace is visible, so the exit could be a crash, a system kill (low memory/thermal) or a normal finish. Task X1: in-app crash/exit diagnostics (UncaughtExceptionHandler log + ApplicationExitInfo reasons + share button) and a hostile-data smoke test of every screen.

---

## D31 — 2026-10-24 — Crash evidence on v0.3.0 (videos, system dialog, triggers)

**Original (verbatim, in order):**

> ۰.۳.۰  (answer: recordings are from v0.3.0)
> اینم نمونه بعدیش  (2nd screen recording, 25 s: Home → App info → app closes via back-to-home animation → reopened, playback paused)
> این یکی دیگه  (screenshot of Android dialog: "مشکلی در موج پیش آمد — موج بسته شد زیرا این برنامه دارای اشکال است")
> موقعی که میخوام یه اهنگو وا کنم / وسط اسکرول تقریبا همه جا

**Summary:** Confirmed real process crashes on v0.3.0 (Android 16-class system dialog). Triggers per owner: (a) tapping a song to start it, (b) mid-scroll almost anywhere. Playback also seemed to stop after leaving the app. Android version not yet given. X1 (crash hunt + diagnostics) and PF-A/PF-B briefed with these triggers; plan: ship diagnostics + crash fixes + perf as v0.3.1 ASAP.

---

## D32 — 2026-10-24 — Owner device is Android 17 / One UI 9; older phones must be good too

**Original (verbatim):**

> اندروردم ۱۷عه وان یو آی ۹
> ولی خب گوشی منه که اپدیت اخره بقیه چی؟ / پس برنامه باید واسه قبلیا هم بهینه باشه

**Summary:** Owner's phone: Android 17, One UI 9 (targetSdk 37 -> Android 17 behaviour changes apply: per-app memory limiter, widget RemoteViews bitmap limit, background audio hardening). But most users have older Android versions and weaker phones: the app (minSdk 26) must be compatible AND optimised across API 26-37, including low-RAM/low-end devices. Task C1: API matrix tests + low-end device profile.

---

## D33 — 2026-10-24 — First real crash log (friend, Note 9 / Android 10): regex + ICU

**Original (verbatim, forwarded from a friend):** "نسخه 0.3.1 برام باگ داره" + "Mowj diagnostics … Samsung SM-N960F (crownlte), Android 10 (SDK 29) … java.lang.ExceptionInInitializerError … Caused by: java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 32 \\([^()]*\\)|\\[[^\\[\\]]*]|\\{[^{}]*}|【[^】]*】 … process up 2 s … breadcrumbs: scan end, screen HomeFragment". Owner: "دوستم اینو گفت".

**Summary:** Root cause of launch/play crashes found with the new Diagnostics screen: `data/lyrics/LyricsQueryNormalizer.BRACKETS` has an unescaped `}`; Android's ICU-backed regex rejects it (the JDK regex used in JVM tests accepts it), a static initializer fails -> ExceptionInInitializerError -> process dies ~2 s after start. Affects v0.3.0 and v0.3.1. Hotfix v0.3.2: escape + audit every regex for ICU compatibility, safe-compile helper, SafeExecutor catches Throwable, a source-scan test for ICU-incompatible regex. (PF-B, branch task/pfb-hotfix-regex.)

---

## D34 — 2026-10-24 — Look at BitChord for useful ideas

**Original (verbatim):** "این پروژه روهم بخون ببین اگه چیزیش بدرد میخپرد استفاده کن: https://github.com/kushagrasinghx/BitChord"

**Summary:** Reviewed BitChord (YouTube Music client, Kotlin, Apple-Music-inspired, **GPL-3.0**). We are MIT + Java-only and the owner's rule is "don't copy others' implementations", so NO code is taken; only ideas, implemented in our own way. Candidate ideas (backlog, after the performance-only releases): crossfade/gapless tuning, 'stats for nerds' audio-format overlay, ListenBrainz/Last.fm scrobbling, optional extra lyrics providers (ToS risk), per-network quality (n/a for local files). Skipped for perf/scope: animated artwork canvas, frosted-glass bars, Discord RPC, streaming sources.
