# Video Web Browser – project context

## What this is
A native Android TV browser (Kotlin + Jetpack Compose for TV) focused on watching videos on
websites with a TV remote. Video playback comes first: smooth full-screen video, remote keys
that control the player, and getting past popups and redirects to reach the video. General
browsing (an on-screen pointer, a scroll mode for dialogs and lists, Back closes site popups)
exists to support that. Personal use on my TCL 55QM5K (Google TV, Android TV OS 12). Also a
public portfolio repo, so code quality, README and CI matter.

## Decisions (already made, don't revisit unless I ask)
- Native Kotlin, not Flutter.
- Compose for TV (androidx.tv) for all app UI; the WebView is a classic View hosted via AndroidView.
- Remote keys are handled in Activity.dispatchKeyEvent, not Compose key modifiers.
- Single module, single Activity, MVVM: one ViewModel exposing a StateFlow of UI state.
- Manual dependency injection with a small AppContainer (no Hilt).
- Settings stored with DataStore Preferences.
- Injected JavaScript lives in assets/js/*.js, not inline strings.
- minSdk 28; compileSdk and targetSdk track the latest stable Android release (currently 37);
  latest stable library versions; JDK 17; Gradle Kotlin DSL + version catalog.
- The repo is generic ("tv-web-shell"): the site URL is set at first run, never hardcoded.
- Only rely on remote keys every remote has: arrows, OK, Back, Play/Pause.
  Hold OK = scroll mode, hold Back = app menu.

## Planned packages
ui/ (Compose screens, pointer overlay, menu), browser/ (WebView setup, navigation policy,
fullscreen video), input/ (input mode state machine, pointer physics, input injector),
data/ (SettingsRepository), assets/js/.

## Milestones
1. Skeleton: TV manifest, Gradle config, banner, GitHub repo, CI building the debug APK
2. Full-screen WebView + first-run URL screen + DataStore settings
3. Pointer mode
4. Scroll mode (JS scroll + swipe fallback)
5. Back handling, full-screen video, media keys
6. Navigation policy (popup/redirect blocking, allowlist), settings screen, menu
7. Polish: zoom, desktop UA, D-pad mode, icon and banner
8. Tests, signed release workflow (GitHub Releases), README with GIF, tag v1.0

## How to work with me
- I'm an experienced Flutter/web developer, newer to native Android. Explain Android-specific
  concepts briefly when they come up (manifest, Gradle, lifecycle, Compose for TV).
- Work in small steps. For each file you create or change, explain what the file or section
  is for and why each important part is there, the way a code walkthrough would. Don't just
  list the changes.
- After each step, tell me how to verify it (build, run on TV, what I should see).
- Never run `git push`, create GitHub repos, or change signing or secrets without asking first.
- Don't add dependencies beyond what the current milestone needs.
- Keep commits small, with conventional commit messages (feat:, chore:, fix:).