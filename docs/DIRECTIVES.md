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
