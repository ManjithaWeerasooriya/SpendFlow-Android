# Quick Add save-flow timing investigation

## Contract and scope

`app/src/androidTest/java/com/example/spendflow/ui/quickadd/QuickAddSaveFlowTest.kt`,
`amountAndCategorySaveUpdatesTotalsAndHistory`, asserts
`SystemClock.elapsedRealtime() - started <= 10_000`.
The timer starts immediately before opening Quick Add and ends when Home exposes
`Expenses: LKR 25.50`. It includes opening/animating the sheet, loading its categories,
field entry, scrolling, saving, and observing the summary update through Compose's
synchronization machinery. It is an end-to-end interaction budget, not a measurement
of the repository write alone. Keep both the timing assertion and persistence/history
assertions intact.

Activity launch, authentication and Firebase profile/category fixture writes happen
before this timer. The server transaction read and Transactions-history checks happen
after it. Readiness already uses observable semantics with `waitUntil`; no fixed sleep
is called by this test flow.

## Observed failure, 2026-11-05

Branch `feature/dashboard-sprint2`, base `33463c9`, including the uncommitted SF-22 work.
Device: Pixel_10 AVD, Android 17 / API 37.1, Windows hypervisor, host OpenGL renderer
using NVIDIA GeForce RTX 3050. The original session was launched through Android
Studio; comparison sessions were standalone/headless cold boots. Auth and Firestore
emulators were running throughout.

Temporary phase instrumentation reproduced an 11,418 ms failure:

| Phase | Wall time |
| --- | ---: |
| Open sheet | 133 ms |
| Wait for category | 5,151 ms |
| Date/details assertions | 28 ms |
| Enter amount | 554 ms |
| Scroll to/category click | 332 ms |
| Scroll to Save | 2,583 ms |
| Save click | 58 ms |
| Wait for Home summary | 2,572 ms |

A read-only semantics-polling experiment still failed at 12,029 ms. A subsequent
stack-sampled run failed at 12,546 ms. During the delayed phases, the Android main
thread was repeatedly inside `HardwareRenderer.nSyncAndDrawFrame`,
`ThreadedRenderer.draw` and `ViewRootImpl.performTraversals`; sheet initialization
also sampled native renderer setup. The instrumentation thread was waiting in
`Instrumentation.runOnMainSync` / Compose test-clock synchronization. Replacing
the polling API did not address these native rendering stalls.

The save implementation validates the draft, queues the Firestore local write,
emits success, and dismisses the sheet. It does not wait for server acknowledgement.
Undo is activity-scoped, deletes the saved transaction through the existing
repository, and retains its five-second/authentication guards. No production defect
in validation, saving, navigation or Undo was established by this investigation.

## Controlled recovery

The original test source and 10-second assertion were restored before these runs:

| Session | Targeted run | Measured interaction |
| --- | ---: | ---: |
| Cold boot, temporary SwiftShader override | 1 | 9,328 ms |
| Same software-rendered session | 2 | 6,173 ms |
| Same software-rendered session | 3 | 4,028 ms |
| Cold boot, original host renderer | 1 | 3,481 ms |
| Same host-rendered session | 2 | 2,297 ms |
| Same host-rendered session | 3 | 2,374 ms |

All six runs passed the original functional and timing assertions. The host-renderer
control shows that changing to software rendering is not necessary for this recovery.
The observed immediate cause was native rendering stalls in the earlier emulator
session; a cold boot recovered the measured behavior. This does **not** establish
whether the underlying cause was a snapshot, embedded-emulator presentation, driver,
resource pressure, or another emulator defect, nor guarantee that it cannot recur.

The subsequent unfiltered `:app:connectedDebugAndroidTest` passed **86/86 tests**,
with no failures or skips, in 4m 32s. Quick Add's measured interaction in that run
was **1,918 ms**. `EntryValidationUndoFlowTest` also passed its invalid-input,
persistence, dismissal, Undo and restored-total assertions.

No production code or test-source changes were retained. The temporary diagnostics
were removed, SF-22's existing work was preserved, and no timing budget or functional
assertion was changed. Unit tests, lint and debug build were not rerun for this
documentation-only change; instrumentation rebuilt/validated the test APK as needed.

## Running the test without changing the contract

If the same renderer stalls recur, stop the test AVD and use a standalone cold-booted
test session, as in the successful runs above. Android Studio's **Cold Boot Now** is
also available, but an embedded cold-boot session was not independently tested here.
Do not wipe its data, change animation scales, or increase the test budget.
For a standalone test emulator, `-no-snapshot` starts a
cold session without loading or saving Quick Boot snapshots; it does not erase
userdata. Keep the existing graphics configuration unless an independent comparison
justifies changing it. Do not start two writable instances of the same AVD.

The successful host-renderer session in this investigation was started with:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Pixel_10 -gpu host -no-snapshot -no-window -no-audio
```

Wait for Android to finish booting; use a second terminal for Gradle. These are
session-only options, not changes to AVD configuration or application code.

With the debug Firebase emulators available, run from the project directory:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.spendflow.ui.quickadd.QuickAddSaveFlowTest'
```

Repeat this unchanged test, then run `:app:connectedDebugAndroidTest` without the
class filter. Do not automatically retry until green or exclude this test from the
full suite. A future failure still needs investigation: record the reported elapsed
time, emulator renderer, and UI/main-thread phase timings before changing behavior.
Use a representative physical device for a product performance conclusion; passing
instrumentation on one emulator alone is not a general performance guarantee.

Android's documentation describes [Compose synchronization and external work](https://developer.android.com/develop/ui/compose/testing/synchronization)
and [emulator snapshots and cold boot](https://developer.android.com/studio/run/emulator-snapshots).
The renderer diagnosis above comes from the sampled local runs, not from those documents.
