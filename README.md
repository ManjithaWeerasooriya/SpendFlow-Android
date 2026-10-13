# SpendFlow

An Android app for tracking spending, built with Kotlin and Firebase (Authentication and Cloud
Firestore). During development the app talks to the local Firebase Emulator Suite, so you don't
need access to the production Firebase project to work on it.

## Prerequisites

- **Android Studio** (a recent version with Android Gradle Plugin 9.3 support) with:
  - Android SDK Platform **37**
  - An Android Virtual Device (AVD), e.g. Pixel 8 on API 37
- **Node.js** 20 or later, and the **Firebase CLI**:
  ```sh
  npm install -g firebase-tools
  ```
- **Java 21 or later** on your `PATH` (the Firestore emulator runs on Java). Gradle provisions its
  own JDK, so this is only needed for the emulators.

## Setup

1. **Clone the repository**
   ```sh
   git clone <repo-url>
   cd SpendFlow
   ```

2. **Add `google-services.json`**

   The build needs `app/google-services.json`. If it isn't in your checkout, ask a maintainer for
   it, or download it from the [Firebase console](https://console.firebase.google.com/):
   project `spendflow-6fd4f` → Project settings → Your apps → Android app
   `com.example.spendflow` → download `google-services.json` into `app/`.

3. **Open the project in Android Studio** and let Gradle sync. Android Studio creates
   `local.properties` with your SDK path automatically.

4. **Log in to Firebase** (first time only):
   ```sh
   firebase login
   ```

## Running the app

1. **Start the Firebase emulators** from the project root and leave them running:
   ```sh
   firebase emulators:start
   ```
   This starts:

   | Service | Address |
   |---|---|
   | Authentication | `127.0.0.1:9099` |
   | Firestore | `127.0.0.1:8080` |
   | Emulator UI | http://127.0.0.1:4000 |

2. **Start an Android Emulator** (AVD) and run the `app` configuration from Android Studio, or:
   ```sh
   ./gradlew :app:installDebug
   ```

3. **Allow the "nearby devices" prompt.** On Android 17 (API 37) and later, debug builds ask for
   local network access so they can reach the emulators on your machine. Tap **Allow**. If you
   deny it, Firestore can't connect and Logcat repeatedly shows
   `UNAVAILABLE: Channel shutdownNow invoked`. To fix, grant it in
   Settings → Apps → SpendFlow → Permissions, or reinstall the app.

4. **Check it works.** Logcat should show `SF-01: Read back: {ok=true}`, and the Emulator UI's
   Firestore tab shows a `test/ping` document.

> Debug builds connect to the emulators through `10.0.2.2`, which is how the Android Emulator
> reaches your computer. This doesn't work on a physical device.

## Tests

```sh
./gradlew :app:testDebugUnitTest            # unit tests
./gradlew :app:connectedDebugAndroidTest    # instrumented tests (needs a running emulator/device)
```

## Troubleshooting

- **`File google-services.json is missing`**: see step 2 of Setup.
- **Firestore `UNAVAILABLE` errors in Logcat**: make sure `firebase emulators:start` is running and
  lists Firestore on port 8080, and that the app was granted the nearby devices / local network
  permission.
- **Emulator fails to start**: check `java -version` reports 21 or later.

## License

[MIT](LICENSE)
