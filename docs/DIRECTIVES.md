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
