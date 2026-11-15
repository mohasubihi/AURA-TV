# AURA Lounge 4 — Android TV

Rebuilt from scratch in `AuraV4Activity.kt`. The old canvas-style home screen and V3 activity were removed.

## What is genuinely interactive

- Native Android TextViews, ImageViews, focusable buttons, and layout elements. UI text is not burned into photography.
- KAFD atmosphere changes based on local Riyadh time and Open-Meteo weather.
- Latest public videos from official Manchester United, Netflix and Rotten Tomatoes Trailers YouTube feeds. Original YouTube images are requested at max resolution with fallback.
- Featured video changes every 30 minutes. Selecting any video opens the TV's YouTube app.
- Installed TV applications appear with their actual Android-provided icons, and can be launched using the remote.
- Manchester United next fixture requires a personal football-data.org token. Without it, the card opens the club fixture schedule instead of claiming a fabricated match.

## Known limitations

The public YouTube feeds are not the personal recommendations or watch history of the account signed into the TV. Netflix integration is not available through a public account library API. Weather may be temporarily unavailable if network requests fail.

This package is an **alpha build** until tested on the physical TCL screen. A successful GitHub Actions compilation does not guarantee a visually polished or crash-free result on the television.

The included KAFD JPEG files are enhanced/upscaled imagery, not verified native 4K photographs; real native text/icon rendering can still be crisp.

## Build

GitHub Actions: `.github/workflows/build-apk.yml`, artifact **AURA-LOUNGE-V4-APK**. Extract its zip to `Downloads/AURA-LOUNGE-V4-APK/app-debug.apk`.

Android package ID: `com.auratv.lounge`. It does not overwrite prior packages.

## Setup

The Android TV must have Wireless debugging enabled and share Wi-Fi with the Windows PC. Open `INSTALL_AURA_LOUNGE_WINDOWS.bat`, enter the active TV ADB serial `IP:PORT`, and install the build.

Configuration is inside the AURA sidebar Settings:
- YouTube channel IDs (comma separated); leave blank to use the three official feeds
- football-data.org API key (stored on the TV, not committed to GitHub)
- automatic or manual KAFD background
- refresh live content

## Source

- `AuraV4Activity.kt`: single-screen native TV layout, focus, navigation, weather/video/app/fixture state.
- `RemoteData.kt`: YouTube public XML feeds, Open-Meteo current weather, football-data.org (requires token).
- `ImageLoader.kt`: HD thumbnails with resolution fallback and bitmap cache.
- `Models.kt`: data classes.

The app does not embed any YouTube or Netflix account credentials.
