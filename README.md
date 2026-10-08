# ToDo

A personal to-do app for Android phones and tablets, built with Kotlin and Jetpack Compose.
It has no accounts: tasks live on the device and, if you turn sync on, in a private app
folder in your own Google Drive so all your Android devices stay in step.

See [FEATURES.md](FEATURES.md) for the feature list.

## Project layout

| Path | What's there |
| --- | --- |
| `core/` | Plain Kotlin, no Android: data model, quick-add parser ("Call mom tomorrow 5pm !high"), repeat rules, Today/Upcoming views, sync merge, JSON/CSV export. Fully unit-tested. |
| `app/` | The Android app: Room database, Compose UI (phone + tablet layouts), reminders, daily summary, Google Drive sync. |
| `.github/workflows/android.yml` | CI: runs the tests and builds the APK on every push. |

## Build

Requirements: JDK 17+ and the Android SDK (API 35). Android Studio has both.

```sh
./gradlew -p core test          # core logic tests, no Android SDK needed
./gradlew :app:assembleDebug    # APK in app/build/outputs/apk/debug/
```

Every push also builds the APK on GitHub Actions; download it from the run's
**todo-debug-apk** artifact and install it on your devices. CI also runs the app on a
phone and a tablet emulator (`app/src/androidTest/`) and keeps the screenshots as the
**device-tests-phone** / **device-tests-tablet** artifacts.

## Releases

**Actions → Release → Run workflow**, enter a version such as `1.1.0`. The workflow runs
the tests, builds the APK and publishes a GitHub release `v1.1.0` with `ToDo-1.1.0.apk`
attached and notes generated from the merged changes. Pushing a tag like `v1.1.0` does
the same. Versions must be `major.minor.patch` and higher than the last one, because
Android only installs updates with a higher version code (1.2.3 → 10203).

## Turning on Google Drive sync (one-time setup)

Sync uses the Google account already on each device. Google only hands the app a Drive
token if a Google Cloud project knows the app's package name and signing key:

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project.
2. **APIs & Services → Library**: enable the **Google Drive API**.
3. **APIs & Services → OAuth consent screen**: user type **External**, fill in the app
   name and your email, add the scope `.../auth/drive.appdata`, and add your own Gmail
   address as a test user.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID → Android**:
   - Package name: `com.hamza.todo`
   - SHA-1: `7B:A4:A9:D7:FE:21:1D:4E:62:7C:D4:B8:6D:71:AD:F5:9E:F2:61:38`

That SHA-1 belongs to `app/debug.keystore`, which every build (local or CI) signs with, so
you only register it once. No client ID needs to go into the code.

Then on each device: **Settings → Sync across my devices**, and approve access when
Google asks. The app can only see its own hidden folder, not your other Drive files.

While the consent screen is in **Testing** status Google may ask you to approve again
from time to time; you can publish it (**In production**) to stop that.

### How sync works

All data is one JSON file in the Drive app-data folder. Each sync downloads it, merges it
with the local data (for every task, list and subtask the newest edit wins; deletes are
kept so they reach every device), saves the result locally and uploads it. Sync runs a few
seconds after each change, when the app opens, and hourly in the background.
