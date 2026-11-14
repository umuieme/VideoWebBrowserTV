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

### Back (temporary, replaced in Milestone 6)

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
