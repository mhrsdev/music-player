# Headset / earbud / car manual checklist

Robolectric cannot route the platform's media-button dispatch, so the buttons are verified in two halves: the unit and
Robolectric tests (`HeadsetButtonsTest`, `HeadsetLogicTest`) prove what the app does with a key; this list proves the
phone actually delivers the key to the app. Do it on a release or debug build, Bluetooth permission granted.

## Set-up

- Settings > Headset: defaults are single = play/pause, double = next, triple = previous, long press = none.
- Disable battery optimisation for Mowj on aggressive vendors (Xiaomi, Oppo, Realme, Samsung "Sleeping apps"): a
  killed or frozen app cannot be restarted by a button on some of them.
- Useful: `adb logcat -s MediaButtonReceiver MediaSessionService MediaSessionImpl`.

## A. No device (adb only)

| Step | Command | Expect |
|------|---------|--------|
| Play a song in the app, lock the screen | | notification with controls |
| Toggle | `adb shell input keyevent 85` (MEDIA_PLAY_PAUSE) | pauses, again plays (about 0.35 s delay) |
| Double press | `adb shell "input keyevent 85; input keyevent 85"` | next track |
| Triple press | `adb shell "input keyevent 85; input keyevent 85; input keyevent 85"` | previous track |
| Next / previous | `adb shell input keyevent 87` / `88` | next / previous at once |
| Play / pause only | `adb shell input keyevent 126` / `127` | plays / pauses |
| Stop | `adb shell input keyevent 86` | pauses, queue and position kept |
| Wired hook key | `adb shell input keyevent 79` (HEADSETHOOK) | same as 85 |
| Seek | `adb shell input keyevent 90` / `89` (FAST_FORWARD / REWIND) | +10 s / -10 s |
| Which session gets the key | `adb shell dumpsys media_session` | under "Sessions Stack" Mowj is first; `active=true`, a `state=PlaybackState {state=...}` and `metadata:` with title / artist / duration; `Media button session is com.mhrsdev.musicplayer` |
| Last receiver after the service stopped | pause, wait 70 s, `dumpsys media_session` | "Media button session is ..." none or Mowj; `mediaButtonReceiver` / `Last MediaButtonReceiver` names `MediaButtonRestartReceiver` |
| Restart from dead | pause, wait 70 s, `adb shell am force-stop com.mhrsdev.musicplayer` is NOT a valid test (force-stop disables receivers until the app is opened). Use `adb shell am kill com.mhrsdev.musicplayer` (or swipe from Recents on a stock ROM), then `adb shell input keyevent 126` | playback resumes the last queue and position, notification appears |

## B. CMF Buds 2a (and any Bluetooth earbuds)

1. Pair, play from Mowj. Tap once: pause; tap again: play. Remember which physical gesture the earbuds map to which
   AVRCP key (the Nothing X / CMF Watch app can reassign them: use "play/pause", "skip forward", "skip back").
2. Double tap and triple tap gestures: next / previous.
3. Pause, wait more than a minute (service stops), lock the phone, tap play on the earbuds: music resumes.
4. Pause, turn the phone's screen off for 10 minutes, tap play.
5. Kill the app from Recents while paused, tap play on the earbuds: resumes the saved queue.
6. Take one earbud out / put the case lid down: with "Pause when headset disconnects" on, music pauses; with
   "Resume when headset connects" on, putting them back within 10 minutes resumes.
7. Phone call: play, receive a call, hang up. "Resume after phone call" on = music continues; off = stays paused.
8. `adb shell dumpsys media_session` while the earbuds are connected: Mowj session `active=true`; also
   `adb shell dumpsys bluetooth_manager | grep -i avrcp` shows the passthrough commands arriving.
9. Long press: left to the phone's assistant unless you mapped it in Settings > Headset (earbud firmware may consume it).

## C. Wired headset / USB-C dongle

1. Plug in while playing: audio moves to the headset, Output chip on the player shows "Headphones".
2. Centre button: single = play/pause, double = next, triple = previous (`HEADSETHOOK`). Volume +/- keep working
   (system). Unplug while playing: pauses (when the setting is on).
3. Headsets with only volume buttons send no media keys: nothing to do in the app.

## D. Car (Bluetooth head unit / Android Auto)

1. Bluetooth: steering wheel next / previous / play / pause behave as the earbud keys; track title and artist show on
   the head unit (metadata is published with the session).
2. Android Auto: Mowj appears as a media app, browse tree (recently added, most played, liked, playlists, albums,
   artists) loads, "Play ..." voice command opens the search.
3. Ignition off and on: the head unit's play resumes the last queue (playback resumption).

## E. Output devices screen (Settings > Headset)

1. Only the speaker connected: the screen shows one device, no picker, the "two Bluetooth devices at once" info card.
2. Connect two outputs (e.g. earbuds + a speaker, or wired + Bluetooth): both listed, the active route marked, the
   volume slider only on the active row; "Switch output" opens the system output switcher.
3. Save sound profile on earbuds (EQ, bass, speed), switch to the speaker (profile is not applied), reconnect the
   earbuds: the saved sound is applied again.
