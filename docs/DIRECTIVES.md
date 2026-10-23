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
