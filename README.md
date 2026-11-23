# VideoWebBrowser

A native Android TV browser built for watching videos on websites with a TV remote.
Kotlin + Jetpack Compose for TV, with the site rendered in a full-screen WebView.

Planned features: smooth full-screen video, remote keys that control the player, blocking the
popups and redirects that get in the way of a video, an on-screen pointer, and a scroll mode
for dialogs and lists. It only relies on keys every remote has (arrows, OK, Back, Play/Pause).
The site URL is chosen at first run; nothing is hardcoded.

## Status

In development. Milestones 1–6 are done:

| # | Milestone | Status |
|---|-----------|--------|
| 1 | Skeleton: TV manifest, launcher banner, CI building the debug APK | Done |
| 2 | First-run URL screen, full-screen WebView, settings (DataStore) | Done |
| 3 | Pointer mode: on-screen pointer moved with the arrows, OK clicks | Done |
| 4 | Video playback: full screen, player remote controls, media keys, screen kept on while playing, DRM (Widevine) | Done |
| 5 | Navigation policy: pop-up, redirect and intent:// blocking, domain allowlist, ad-domain blocking | Done |
| 6 | Scroll mode, Back handling (closes site popups, goes back), hold Back detection | Done |
| 7 | Settings screen and menu, zoom, desktop user agent, D-pad mode, icon and banner | Next |
| 8 | Tests, signed releases, README with GIF, tag v1.0 | Planned |

### Remote controls so far

| Key | On a page | Video in full screen |
|-----|-----------|----------------------|
| Arrows | Move the pointer; pushing it against an edge scrolls the page | Left/Right seek 10 s (30 s when held); Up/Down show the pointer |
| OK | Click under the pointer | Play/pause |
| Hold OK | Scroll mode: arrows scroll whatever is under the pointer (dialogs, lists, rows, the page). OK, hold OK or Back leaves it | — |
| Back | Close the site's popup; otherwise previous page; on the first page, press twice to exit | Hide the pointer, then leave full screen |
| Hold Back | App menu (coming in Milestone 7) | App menu |
| Play/Pause | Controls the video on the page | Play/pause |

If the site won't load, the error screen offers **Retry** and **Change site**.

The manual test checklist for the TV is in [docs/TESTING.md](docs/TESTING.md).

## Requirements

- Android Studio (latest stable) or the command line with JDK 17
- Android SDK with the platform set in `app/build.gradle.kts` (`compileSdk`)
- A Google TV / Android TV device running Android 9 (API 28) or newer

## Build

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew lint                 # report at app/build/reports/lint-results-debug.html
./gradlew test                 # unit tests
```

CI (GitHub Actions) runs the same two tasks on every push to `main` and on pull requests,
and attaches the debug APK to the run as an artifact.

## Install on a TV with adb

1. On the TV, enable developer options: **Settings → System → About**, select
   **Android TV OS build** 7 times.
2. Turn on **Settings → System → Developer options → USB debugging**
   (required even when you only use Wi-Fi).
3. Connect with one of the methods below, then install:

### Option A – Wireless debugging (pairing code, Android 11+)

1. **Developer options → Wireless debugging** → turn it on.
2. Select **Pair device with pairing code**. The TV shows an `IP:port` and a 6-digit code.
3. On your computer (same network):

   ```bash
   adb pair <TV_IP>:<PAIRING_PORT>      # enter the 6-digit code
   adb connect <TV_IP>:<CONNECT_PORT>   # the IP:port on the main Wireless debugging screen
   ```

   The pairing port and the connect port are different; pairing is needed only once per computer.

### Option B – Network debugging (older TVs)

```bash
adb connect <TV_IP>:5555
```

Accept the "Allow debugging?" prompt on the TV.

### Install and launch

```bash
adb devices                                       # the TV should be listed as "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.umuieme.videowebbrowser -c android.intent.category.LEANBACK_LAUNCHER 1
```

Or run the `app` configuration from Android Studio with the TV selected as the device.

## License

[MIT](LICENSE)
