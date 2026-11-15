# AURA TV — TCL Android TV Launcher (prototype v0.1)

A premium RTL Android TV home screen built in **Kotlin with native Android Views** (no third-party dependencies).

## Implemented
- Cinematic procedural skyline backgrounds with **Automatic / Night / Aurora / Sunset** themes.
- Optional personal wallpaper from Android's file picker (no storage permission).
- Large live clock, Gregorian + Umm al-Qura Hijri date and editable city label.
- Discovers launchable installed TV apps, displays their actual application icons and opens them.
- Remote D-pad navigation: focus highlights, OK opens; **Menu** on an app opens its management actions.
- Move app forward/back in the list or hide it; restore hidden apps through Settings.
- Accent selection (champagne / ice blue / lavender), persisted with SharedPreferences.
- Leanback launcher intent and separate HOME candidate intent.
- No ads, no fake weather values, no prayer calls or audio, no analytics and no network calls.

## Build
1. Open this folder with **Android Studio** and let it sync Android SDK + Gradle.
2. Use **JDK 17**, **Android SDK Platform 35** and **Gradle 8.11.1+** (Android Studio can provision Gradle).
3. Build > Build APK(s), or run `gradle assembleDebug` if Gradle is already installed.
4. Output: `app/build/outputs/apk/debug/app-debug.apk`.
5. Install with `adb install -r app-debug.apk` or transfer the APK to your TV.
6. Launch **AURA TV** from the installed apps; use the remote to control it.

## Important: making it the Home screen
The Android `CATEGORY_HOME` intent is present, but whether TCL Android TV allows changing the default launcher depends on its firmware. **Do not disable or remove TCL's existing launcher during initial testing.** First verify AURA TV works as a regular app. If Android offers "default home app", you may select it; if not, this prototype will still work as an app without replacing Home.

## Preview
Open `preview/aura-tv-demo.html` in a desktop browser. This is an interactive *visual demo*, not an Android APK. Use arrow keys and Enter, or a mouse.

## Current limitations
- A debug APK **is not included**, because the build environment did not have Android SDK/Gradle available. The Android source project is supplied for building in Android Studio.
- No real weather API (deliberate: avoid fabricated forecast data), and there are no prayer/adhan features.
- Device-compatibility testing on your specific TCL model and firmware remains required.
- The procedural wallpaper avoids using someone else's images. Import a personal photo for true photographic backgrounds.
