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
- Long-press OK logs only: `adb logcat -s PointerLayer`.
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
| 3.6 | Hold an arrow until the pointer reaches the screen edge; keep holding. | The pointer stops with its tip at the edge and never leaves the screen. Nothing else happens yet (edge scrolling is planned for Milestone 4). |
| 3.7 | Hold an arrow, then release it. | The pointer stops immediately; it does not drift. |
| 3.8 | Hold an arrow and press Home while holding; release; reopen the app. | The pointer is not moving on return. |

### Click and hover

| # | Steps | Expected result |
|---|-------|-----------------|
| 3.9 | Move the pointer onto a link and press OK. | A short white ring pulses at the tip and the link opens. |
| 3.10 | Move the pointer over a menu that opens on mouse hover. | The menu opens while the pointer is over it, without pressing OK. |
| 3.11 | On a video page, move the pointer over the paused player. | The player's controls appear. |
| 3.12 | Click the player's play button (or the video itself). | The video starts playing, with sound. |
| 3.13 | Click the site's fullscreen button on the player, if it has one. | The site's fullscreen request is accepted (full-screen video handling itself is Milestone 5). Back still works. |
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
