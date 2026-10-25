# SpendFlow

An Android app for tracking spending, built with Kotlin and Firebase (Authentication and Cloud
Firestore). During development the app talks to the local Firebase Emulator Suite, so you don't
need access to the production Firebase project to work on it.

## Prerequisites

- **Android Studio** (a recent version with Android Gradle Plugin 9.3 support) with:
  - Android SDK Platform **37**
  - An Android Virtual Device (AVD), e.g. Pixel 8 on API 37
- **Node.js** 22 or later, and the **Firebase CLI**:
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

### Firestore security rules

`firestore-tests/` checks `firestore.rules` against the Firestore emulator: owner-only access
under `users/{uid}`, denial for other and unauthenticated users, and the field validation for
every entity. It starts its own emulator on port 8180 under the `demo-spendflow` project, so it can
run while `firebase emulators:start` is up and never touches real data.

```sh
cd firestore-tests
npm ci
npm run test:emulator
```

Run it after any change to `firestore.rules` or a mapper in `data/remote/mapper/`. CI runs it on
every pull request.

## Troubleshooting

- **`File google-services.json is missing`**: see step 2 of Setup.
- **Firestore `UNAVAILABLE` errors in Logcat**: make sure `firebase emulators:start` is running and
  lists Firestore on port 8080, and that the app was granted the nearby devices / local network
  permission.
- **Emulator fails to start**: check `java -version` reports 21 or later.

## License

[MIT](LICENSE)

### Income in multiple currencies (SF-09)

Income Quick Add supports original currency, a manual exchange rate (base currency per unit), and a live approximate base amount. With an LKR profile, USD 500 at 300 saves USD 50,000 minor units and LKR 15,000,000 minor units. Existing transaction fields `amount`, `currency`, `rate`, `baseAmount`, and `baseCurrency` preserve this snapshot; historical records are never repriced. The last successfully saved rate for each currency pair is remembered on this device. Income sources created in Categories can specify a default currency. Deploy the updated `firestore.rules` to allow that optional field.

?Use latest rate? calls [ExchangeRate-API's open endpoint](https://www.exchangerate-api.com/docs/free), with no API key. Rates update daily, are indicative, and are cached in memory until the provider's next update. All supported provider currencies can be converted to LKR; unsupported currencies or network errors leave manual entry available. The currency selector lists spendable ISO currencies, so provider coverage may be narrower. Provider attribution is shown beside the rate. Conversion follows the profile base currency (normally LKR).

### Entry validation and Undo (SF-10)

Save validates every required field and shows errors beside the amount, category or income source, and foreign-currency rate. Amounts must be positive plain decimal numbers, have at most two decimal places (zero for JPY and other currencies with no fractional unit), and both original and converted amounts must be at most 999,999,999.99. Validation failures create no transaction.

After a local save, Quick Add closes and the activity shows ?Saved - Groceries LKR 2,450? with Undo for five seconds. Undo deletes that exact transaction; the existing Firestore snapshot listeners refresh the transaction list and monthly totals. Confirmation and Undo survive sheet dismissal and activity recreation. Failed deletes and delayed server rejections display an error. Like other app writes, offline changes are stored locally and sync when connectivity returns.

`EntryValidationUndoFlowTest` runs against the debug Auth/Firestore emulators and checks inline errors, blocked invalid writes, the save snackbar, Undo deletion, and restored totals on an Android device.

### Savings goals and completion notifications

More > Goals lets a signed-in user create a savings target and update the total saved so far, in their profile base currency. This is manual goal progress, tracked separately from transaction totals. Reaching or exceeding the target posts a system notification after Firestore acknowledges the update. Tap the notification to open Goals.

Enable goal notifications on the Goals screen; Android 13+ asks for permission, and disabled channels can be restored in system notification settings. Completion history is stored per user and goal on this device, so repeated snapshots, edits, and app restarts do not repeat an alert. A denied notification is retried when permission is enabled. Clearing app data or reinstalling resets this local history.

Goal monitoring runs while the app process is alive and checks saved completions again on the next launch. This does not include server push delivery while the app is fully stopped. Deploy the updated `firestore.rules` before using goals against production Firebase; goal documents live at `users/{uid}/goals/{id}`.
