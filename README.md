# LifeOS

An offline personal life tracker for Android: daily habits, to-dos, weekly /
monthly / yearly tasks, goals with monthly contributions, a life map with
improvement tips, income and expenses, budget, net worth, health log,
journal and reminder notifications.

All data stays on the phone. Use **More → App & backup** to save a copy.

## Install

Download the latest `.apk` from [Releases](../../releases/latest) on your
phone and open it. The app tells you when a newer version is out.

## Build

- UI: `app/src/main/assets/index.html` (single file, no build step)
- Native: WebView shell, AlarmManager reminders, update check (`app/src/main/java/...`)
- CI: `.github/workflows/build-apk.yml` builds and releases on every push to `main`.
  It needs the `SIGNING_KEYSTORE_B64` repository secret (base64 of the signing keystore).
