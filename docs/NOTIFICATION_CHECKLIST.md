# Media notification / background playback: real-device checklist

Package: `com.mhrsdev.musicplayer` (grep for `mowj` in dumps if the applicationId differs: `adb shell pm list packages | grep -i mowj`).

## How it works (one paragraph)
`ExoPlayerController.play/resume/skipTo` call `startService(PlaybackService)`. `PlaybackService.onCreate` builds the
`MediaSession` on the shared `PlayerHolder` player and registers it with `addSession(...)`; Media3 then posts the media
notification on channel `playback` and calls `startForeground` while playing. Paused: foreground is dropped, the
notification stays (swipeable) and the service stops itself after 60 s (queue + position are persisted).
Root cause of the original bug: the session was never added to the service (no `MediaController` ever connects, so
`onGetSession` never ran), hence no notification, no foreground state, and the process could be killed on leaving the app.

## Checks (all with the app installed from a debug/release build, Persian UI is fine)
1. **Shade card**: play a track, pull down the shade. Expect a media card with cover, title, artist, prev / play-pause / next.
2. **Lock screen**: lock the phone. Expect the same controls on the lock screen; play/pause and next work.
3. **Samsung Now Bar / Always-on / edge panel** (One UI 7+): controls and cover appear; tapping opens the app.
4. **Bluetooth / headset buttons**: play, pause, next, previous from a headset; also after the app was swiped away while paused.
5. **Leaving the app**: Home button, app switcher, screen off: music keeps playing. Swipe the app from Recents while
   *playing*: music keeps playing, notification stays. Swipe away while *paused*: notification disappears at once or at most after 60 s.
6. **Android 13/14+ notification permission**: first play shows the permission prompt.
   - *Granted*: the card appears immediately after granting (the activity re-starts the service to re-post it).
   - *Denied*: playback still works and the system media controls (shade/lock screen) still show from the active session; no app notification is shown.
7. **Swipe-away of the notification** while paused: allowed; playing: not dismissible on most versions.
8. **Track change**: title/artist/cover update; seek from the card moves the position in the app.
9. **After process death**: `adb shell am kill com.mhrsdev.musicplayer` while paused, press a headset play button: the queue and position restore and playback starts.
10. **Battery**: no wake lock while paused, no repeated wakeups (see below).

## adb commands
```
adb shell dumpsys media_session | grep -A25 -i mowj            # session active=true, state=PLAYING(3), metadata title/artist
adb shell dumpsys notification --noredact | grep -B2 -A30 mowj # channel playback, MediaStyle, actions, smallIcon
adb shell dumpsys activity services com.mhrsdev.musicplayer     # PlaybackService: isForeground=true while playing, foregroundServiceType=0x2
adb shell dumpsys power | grep -i -B1 -A3 mowj                  # wake locks: only "ExoPlayer:WakeLockManager" while playing
adb shell dumpsys batterystats --charged | grep -i mowj         # long-run drain
adb logcat -s MediaSessionService ForegroundServiceTypeLoader ActivityManager | grep -i mowj
```

## Troubleshooting: playback stops when the app is left (Samsung, Xiaomi, Huawei, Oppo)
Vendor battery managers may kill a foreground service anyway. The app deliberately does **not** ask for a battery
optimization exemption. If it still happens, ask the user to:
- Samsung: Settings > Battery > Background usage limits > remove the app from *Sleeping/Deep sleeping apps*; App info > Battery > *Unrestricted*.
- Xiaomi/MIUI/HyperOS: App info > Battery saver > *No restrictions*; Security > Autostart > enable the app; lock the app in Recents.
- Others: App info > Battery > *Don't optimize* / *Allow background activity*.
Confirm first with `dumpsys activity services` that the service really was foreground, so it is not an app bug.
