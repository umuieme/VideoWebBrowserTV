# Manual TV test plan

Manual test cases to run on the TV (TCL 55QM5K, Google TV, Android TV OS 12) before release.
Automated checks (`./gradlew assembleDebug lint test`) run on every change and in CI; this file
covers what only a real TV and remote can verify.

## Setup

- Install the debug build from Android Studio, or with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Package name: `com.umuieme.videowebbrowser`.
- Reset to first-run state: `adb shell pm clear com.umuieme.videowebbrowser`.
- Logcat (debug builds): `adb logcat -s VWB-Calibration`, or in Android Studio `package:mine tag:VWB-Calibration`.

---

## Milestone 1: Skeleton

| # | Steps | Expected result |
|---|-------|-----------------|
| 1.1 | Open the Google TV home screen and go to Apps. | The app is listed, with its banner (not a generic icon). |
| 1.2 | Launch the app from the launcher. | The app opens in landscape with no white flash. |

## Milestone 2: First-run setup, full-screen WebView, settings

### First run and setup screen

| # | Steps | Expected result |
|---|-------|-----------------|
| 2.1 | `pm clear`, then launch the app. | "Choose your website" screen. The URL box has a highlighted border and shows "Press OK to type". The keyboard is **not** open. |
| 2.2 | From 2.1, press Down, then Up. | Focus moves to "Open", then back to the URL box. The keyboard never opens by itself. |
| 2.3 | With the URL box focused, press OK. | The on-screen keyboard opens with a URL layout. |
| 2.4 | Type `example.com` and press the keyboard's Go/Done key. | The keyboard closes and the site opens full screen. |
| 2.5 | During 2.4, watch the top of the screen. | A thin coloured progress bar appears while the page loads, then disappears. Background stays black (no white flash). |
| 2.6 | While typing, press Back once, then Back again. | First Back closes the keyboard (text kept). Second Back leaves the field; the box stays highlighted. |
| 2.7 | While typing with the keyboard closed (after one Back), press OK. | The keyboard reopens. |
| 2.8 | Type a URL, press Back to close the keyboard, press Down and then OK on "Open". | The site opens. |

### Invalid input

| # | Steps | Expected result |
|---|-------|-----------------|
| 2.9 | Leave the box empty and press "Open". | Red message: "Enter a website address." |
| 2.10 | Enter `youtube` and submit. | "That doesn't look like a website address…" |
| 2.11 | Enter `ftp://example.com` and submit. | "Only http and https addresses are supported." |
| 2.12 | After any error, type one more character. | The error disappears. |
| 2.13 | Enter `  example.com  ` (spaces around it) and submit. | Accepted and opens `https://example.com`. |

### Relaunch and lifecycle

| # | Steps | Expected result |
|---|-------|-----------------|
| 2.14 | With a site open, press Home, wait a few seconds, reopen the app. | Same page, same scroll position, no reload. |
| 2.15 | Play a video, press Home. | Audio and video stop. |
| 2.16 | Force-stop the app (Settings › Apps), then launch it. | The saved site opens directly; no setup screen. |
| 2.17 | Look at the screen edges while browsing. | No status or navigation bars; the page uses the full screen. |

### Errors

| # | Steps | Expected result |
|---|-------|-----------------|
| 2.18 | Turn off Wi-Fi, force-stop and relaunch the app. | "Couldn't load this page" with a `net::ERR_…` detail; "Retry" is focused. |
| 2.19 | Turn Wi-Fi back on and press Retry. | The page loads normally. |
| 2.20 | `pm clear`, set up `https://nothing.invalid`. | The error panel appears (`net::ERR_NAME_NOT_RESOLVED`). |
| 2.20a | On that error panel, press Right. | Focus moves from Retry to "Change site". |
| 2.20b | Press OK on "Change site". | The setup screen opens with `https://nothing.invalid` already in the field. |
| 2.20c | Press Back on that setup screen (keyboard closed, not editing). | Returns to the browser, which tries the saved URL again (error panel again). |
| 2.20d | Open "Change site" again, enter a working URL and press Open. Then force-stop and relaunch. | The new site loads, and it is still the one opened after the relaunch. |
| 2.20e | Open "Change site", press OK on the field, press Back once, then Back again. | The first Back closes the keyboard / stops editing; the second returns to the browser. |
| 2.21 | (Optional, emulator only) Disable "Android System WebView" and launch. | "Web browser component unavailable" with Retry; no crash. |

### Back (temporary, replaced in Milestone 5)

| # | Steps | Expected result |
|---|-------|-----------------|
| 2.22 | Open a link on the site, then press Back. | Returns to the previous page. |
| 2.23 | On the first page, press Back once. | Toast: "Press Back again to exit". The app stays open. |
| 2.24 | Press Back twice within 2 seconds. | The app exits. |
| 2.25 | Press Back once, wait more than 2 seconds, press Back once. | Toast shown again; the app does not exit. |

### Calibration log (debug build)

| # | Steps | Expected result |
|---|-------|-----------------|
| 2.26 | With the Logcat filter above, load any page. | A line like `{"innerWidth":…,"innerHeight":…,"devicePixelRatio":…} url=…` per page load. Note the values for Milestone 3. |

## Milestone 3: Pointer mode

Notes:
- Use the physical remote. Key events from `adb shell input keyevent` carry the virtual-keyboard
  device id, which the app deliberately passes through, so they don't move the pointer.
- Long-press OK logs only: `adb logcat -s RemoteInputLayer`.
- Good test pages: a site with a CSS hover menu (most news/shop sites), a video page whose player
  hides its controls until hovered, and a page with a search box.

### Movement

| # | Steps | Expected result |
|---|-------|-----------------|
| 3.1 | Open the site and wait for it to load. | A white arrow pointer with a dark outline is in the centre of the screen. Easy to see from the couch. |
| 3.2 | Tap Right once quickly (press and release). | The pointer moves a tiny step (about the width of the arrow's tail). Several taps give precise small steps. |
| 3.3 | Hold Right for about 2 seconds. | After a short pause the pointer starts slowly, speeds up smoothly over about a second, then glides fast. No stutter. |
| 3.4 | Hold Left, Up and Down in turn. | Same behaviour in every direction. |
| 3.5 | Hold Up, then press and hold Right as well. | The pointer moves diagonally up-right at the same speed as a straight move. **Remote limitation:** many TV remotes report only one key at a time; if the pointer just switches to moving right, that is the remote, not the app. |
| 3.6 | Hold an arrow until the pointer reaches the screen edge; keep holding. | The pointer stops with its tip at the edge and never leaves the screen. Nothing else happens yet (edge scrolling is planned for Milestone 5). |
| 3.7 | Hold an arrow, then release it. | The pointer stops immediately; it does not drift. |
| 3.8 | Hold an arrow and press Home while holding; release; reopen the app. | The pointer is not moving on return. |

### Click and hover

| # | Steps | Expected result |
|---|-------|-----------------|
| 3.9 | Move the pointer onto a link and press OK. | A short white ring pulses at the tip and the link opens. |
| 3.10 | Move the pointer over a menu that opens on mouse hover. | The menu opens while the pointer is over it, without pressing OK. |
| 3.11 | On a video page, move the pointer over the paused player. | The player's controls appear. |
| 3.12 | Click the player's play button (or the video itself). | The video starts playing, with sound. |
| 3.13 | Click the site's fullscreen button on the player, if it has one. | The video goes full screen (details in Milestone 4). |
| 3.14 | Click a text field (e.g. the search box). | The on-screen keyboard opens. |
| 3.15 | Type a word with the keyboard and submit (Go/Search key). | The text appears in the field and the search runs. |
| 3.16 | With the keyboard open, use the arrows. | The arrows move within the keyboard, not the pointer. |
| 3.17 | Press Back to close the keyboard, then move the pointer and click the field again. | The pointer moves normally after the keyboard closes; clicking reopens the keyboard. |
| 3.18 | Hold OK for about 1 second, then release. | No click happens. Logcat shows `Long-press OK`. |
| 3.19 | Click a link and wait for the new page to load. | The pointer is still where it was before the page changed (not re-centred). |

### Auto-hide

| # | Steps | Expected result |
|---|-------|-----------------|
| 3.20 | Leave the remote alone for 5 seconds. | The pointer fades out smoothly. Any open hover menu stays open. |
| 3.21 | After 3.20, press any arrow. | The pointer reappears instantly at the same place and moves. |
| 3.22 | After 3.20, press OK once. | The pointer reappears but nothing is clicked. A second OK clicks. |
| 3.23 | Keep holding an arrow for longer than 5 seconds. | The pointer never fades while moving. |

### Other screens keep normal focus navigation

| # | Steps | Expected result |
|---|-------|-----------------|
| 3.24 | `pm clear`, launch the app. | Setup screen: no pointer; Up/Down move focus between the URL box and "Open" as in 2.2. |
| 3.25 | On the setup screen, highlight "Open" with a valid URL and press OK. | The site opens; the OK release does **not** click anything on the new page. |
| 3.26 | Trigger a load error (as in 2.18). | Error panel with no pointer; Retry is focused and OK presses it. |
| 3.27 | After Retry succeeds. | The pointer is back, at the position it had before the error. |
| 3.28 | On any page, press Back. | Back behaves exactly as in 2.22–2.25 (pointer mode doesn't touch Back). |

## Milestone 4: Video playback

Notes:
- Use the physical remote (see Milestone 3 notes about `adb shell input`).
- Debug log of which path handled a media command: `adb logcat -s VWB-Media`. Lines look like
  `TogglePlay -> Agent (fullscreen=true)`; `NativeKeys` = key fallback, `PassThrough` = key left to the page.
  The first line after launch says whether the agent was installed or is unsupported by this WebView.
- Test pages:
  - **Plain HTML5 video (same origin):** `https://www.w3schools.com/html/html5_video.asp`
  - **Cross-origin iframe player:** `https://www.w3schools.com/html/html_youtube.asp` (embedded YouTube), or any news article with an embedded YouTube/Vimeo player.
  - **DRM (Widevine):** `https://shaka-player-demo.appspot.com`, pick a Widevine asset (e.g. "Angel One (multicodec, multilingual, Widevine)").
  - **Your own site** (the one set at first run): repeat the full-screen and key tests there.

### Enter and exit full screen

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.1 | On a video page, start the video and click the player's full-screen button with the pointer. | The video fills the screen, no system bars. The pointer disappears. The video does **not** pause when you release OK. |
| 4.2 | In full screen, press Back. | Full screen closes; the page is shown as before, the pointer works, the page did not navigate back. |
| 4.3 | Enter full screen again, move the pointer over the video (Up), click the player's exit-full-screen button. | Full screen closes. Arrows move the pointer normally afterwards. |
| 4.4 | Enter and exit full screen five times quickly (button, Back, button, Back...). | No black screen, no frozen video, no crash. After the last exit the page responds normally. |
| 4.5 | On the page (not full screen), press Back. | Normal browsing Back (history / "Press Back again to exit"), as in 2.22–2.25. |

### Video control in full screen

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.6 | In full screen, press OK. | The video pauses; the OSD at the bottom shows "❚❚ Paused" and current / total time. Press OK again: plays, OSD shows "▶ Playing". |
| 4.7 | Press Right once. | Jumps forward about 10 s; OSD shows "+10 s" and the new time. |
| 4.8 | Press Left once. | Jumps back about 10 s; OSD shows "−10 s". |
| 4.9 | Hold Right for about 4 seconds, then release. | Steps of 10 s every ~0.4 s, then after ~2 s steps of 30 s. The OSD total keeps growing ("+40 s", "+70 s"...). Seeking stops immediately on release. |
| 4.10 | Leave the remote alone after an action. | The OSD disappears after about 2 s. It never steals focus (OK still toggles play right after it shows). |

### Pointer over the full-screen video

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.11 | In full screen, press Up (or Down). | The pointer appears where it was; the player shows its controls. |
| 4.12 | Move the pointer to the player's settings/CC button and click; change subtitles or quality. | The menus open and the change applies. Arrows move the pointer (they do not seek) while it is shown. |
| 4.13 | Press Back once. | The pointer hides; you're back in video control (OK = play/pause, Left/Right = seek). Full screen stays. |
| 4.14 | Press Back again. | Full screen closes. |

### Media keys

If your remote has no separate media keys, a Bluetooth keyboard or the Google TV phone remote app with a play/pause button works too.

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.15 | On a page with a playing inline (not full-screen) video, press Play/Pause. | The video pauses, OSD shows "❚❚ Paused" and the time. Press again: plays. |
| 4.16 | In full screen, press Play/Pause, then (if available) Fast-forward and Rewind. | Play/pause toggles; FF/Rewind seek ±10 s with OSD. |
| 4.17 | (If available) With the video playing press Play: nothing changes. Press Pause twice. | Play never pauses; Pause never resumes a paused video. |

### Cross-origin iframe

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.18 | Open the iframe test page; start the embedded video by clicking it. | It plays. |
| 4.19 | Press Play/Pause (inline). | The embedded video pauses and resumes; OSD shows time / duration. Logcat shows `-> Agent`. |
| 4.20 | Click the embedded player's full-screen button; test OK, Left/Right, Up + Back, Back. | Same behaviour as 4.6–4.14. |

### Page without video (fallback)

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.21 | On a page with no video (e.g. `https://example.com`), press Play/Pause. | Nothing happens: no scrolling, no OSD, no crash. Logcat shows `-> PassThrough`. |
| 4.22 | On a page with a long article and no video, press Play/Pause several times. | The page does **not** scroll. |

### Screen stays on only while playing

Set the TV's screensaver to its shortest delay first (Settings › System › Ambient mode / Screensaver › Start after). To check without waiting: `adb shell dumpsys window windows | grep -E "mCurrentFocus|KEEP_SCREEN_ON"`.

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.23 | Play a video (inline or full screen) longer than the screensaver delay; don't touch the remote. | The screensaver never starts. `dumpsys` shows `KEEP_SCREEN_ON` for the app's window. |
| 4.24 | Pause the video and wait past the delay. | The screensaver starts normally. `KEEP_SCREEN_ON` is gone while paused. |
| 4.25 | Let a video play to its end and wait. | The screensaver starts after the delay. |

### DRM

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.26 | On the Shaka demo, play a Widevine asset. | It plays (no "permission" or EME error). Expect limited resolution: WebView normally gets software Widevine (L3), so commercial services usually cap at SD–720p and may refuse some titles. |
| 4.27 | Open a page that asks for the camera or microphone (e.g. `https://webcamtests.com`). | Access is denied silently; the app does not crash or show a prompt. |

### Renderer crash (optional, emulator only)

| # | Steps | Expected result |
|---|-------|-----------------|
| 4.28 | With a page open on an emulator: `adb root`, then `adb shell pkill -f sandboxed_process`. | "This page stopped working" with Retry focused; the app does not crash. Retry loads the page in a new WebView. |
