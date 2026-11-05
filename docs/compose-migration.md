# SpendFlow Compose migration

## Current scope

Every screen is Jetpack Compose with Material 3, hosted by a single Compose `MainActivity` with Navigation Compose (Phase 2 done). What remains in XML is the View component library, its layouts and the debug gallery (Phase 3). The migrations did not change ViewModels, repositories, Firebase or business logic.

The goal is to remove every XML layout. The work runs in four phases:

0. **Foundations — done.** Kotlin colour tokens and the Compose component library (below).
1. **Screens inside their Fragments — done.** More, Login, Categories (including `dialog_income_source.xml` and `menu/income_source_actions.xml`), Transactions + Home, Goals, Analytics (chart in `AndroidView` at first), Quick Add. Port each screen's instrumented test to `compose-ui-test` in the same task.
2. **Shell swap, as one change — done.** `MainActivity` becomes a `ComponentActivity` with a `Scaffold` (Compose bottom bar, `SnackbarHost`, insets), Navigation Compose replaces the XML graphs, and Quick Add becomes a `ModalBottomSheet`. Add `navigation-compose` and `hilt-navigation-compose` here. All Fragments, navigation graphs, menus and `activity_main.xml` are deleted.
3. **Cleanup.** Canvas chart, Compose gallery, delete View components, remaining layouts, `styles_components.xml`, `type.xml`, `attrs.xml` and `colors.xml` (with `ThemeColorsSyncTest`), turn off `viewBinding`, drop RecyclerView/ConstraintLayout/Fragment/Navigation-Fragment dependencies.

## Migration progress

- **Settings — migrated:** `SettingsFragment` hosts `SettingsRoute` in a `ComposeView` with `DisposeOnViewTreeLifecycleDestroyed`. `SettingsScreen` accepts immutable display state and a Back callback. Light and dark previews use sample display state without Firebase or a ViewModel. Settings has no existing ViewModel or asynchronous state to collect. MainActivity continues to own system-bar and keyboard insets. The shared `fragment_placeholder.xml` and `PlaceholderFragment` are retained.
- **Onboarding — migrated:** `OnboardingFragment` hosts `OnboardingRoute` in a lifecycle-disposed `ComposeView`, preserving its Fragment-scoped `OnboardingViewModel`. The stateless `OnboardingScreen` accepts currency state and Skip/Continue callbacks; both callbacks still call `finish()`, leaving completion/navigation to the existing auth and MainActivity flow. The ViewModel has a fixed value and no state/event Flow to collect. The currency remains read-only with a localized display name. Light, dark and large-text previews have no Firebase or ViewModel dependency. The content scrolls on smaller displays; MainActivity owns system-bar/keyboard insets. `fragment_onboarding.xml` is retained pending user confirmation.
- **Phase 0 foundations — done:**
  - `ui/theme/Color.kt` holds every `sf_*` token as `SpendFlowColors` (light and dark palettes) and derives the Material colour schemes from them with the same role mapping as `values/themes.xml`. Composables read brand roles through `SpendFlowTheme.colors`.
  - `ColorContrastTest` checks the Kotlin palettes. `ThemeColorsSyncTest` fails if `colors.xml`/`values-night/colors.xml` drift from them while Views still read the XML. `ThemeOnlyColorsTest` also rejects `Color(0x…)` outside `ui/theme/`.
  - Compose components in `core/ui/components/`, each with light/dark previews: `AmountText`, `PrimaryButton`/`SecondaryButton`/`DestructiveButton`, `EmptyState`, `LabeledProgress`, `SummaryCard` (the compact `MonthlySummary` layout the app uses, with the optional expense-ratio bar), and, beside their View classes of the same name, `CategoryChip`, `LabeledSwitch`, `SpendFlowSnackbar` (+ `SpendFlowSnackbarVisuals`) and `SpendFlowBottomBar`.
  - Compose UI test libraries added (`ui-test-junit4` for androidTest, `ui-test-manifest` for debug).
  - Several vector drawables (`ic_badge_*`, `ic_status_*`, `bg_*`, `ic_category_*`) take colours from `?attr/sfColor*`. Compose resolves them through the Activity theme for now; they need Kotlin-side tinting or recolouring before `attrs.xml` is removed in Phase 3.
- **More — migrated (Phase 1):** `MoreFragment` hosts `MoreRoute` in a lifecycle-disposed `ComposeView`, keeping the Fragment-scoped `MoreViewModel` and the existing nav-graph actions to Categories, Goals and Settings. `MoreScreen` is stateless apart from the log-out confirmation, now a Compose `AlertDialog` held in `rememberSaveable`, so it survives recreation (the old Material dialog was dismissed). Dialog buttons use `violetText` because violet fails as text on the dark dialog surface. `GoalCompletionNotificationFlowTest` now opens Goals through a Compose test rule instead of `R.id.goalsRow`. `fragment_more.xml` is retained pending user confirmation.
- **Login — migrated (Phase 1):** `LoginFragment` hosts `LoginRoute`, keeping the Fragment-scoped `LoginViewModel` unchanged; leaving the screen is still MainActivity's job. `LoginScreen` holds the typed text in `rememberSaveable` (as EditTexts saved theirs) and passes it to `submit`/`sendPasswordReset`; field errors, the busy state and mode come from `LoginUiState`. Events are collected with the new `core/ui/EventEffect` and shown in the screen's own `SnackbarHost` with `SpendFlowSnackbar`. Autofill uses Compose content types (email, password, new password). New shared pieces: `LabeledTextField` (Widget.SpendFlow.TextField) and `ic_visibility`/`ic_visibility_off` for the password toggle, since Compose has no `password_toggle` end icon. No existing instrumented test drove the Login UI, so `LoginScreenTest` (5 tests) covers the stateless screen. `fragment_login.xml` is retained pending user confirmation.
- **Categories — migrated (Phase 1):** `CategoriesFragment` hosts `CategoriesRoute`, keeping both Fragment-scoped ViewModels (`CategoriesViewModel`, `ExpenseCategoriesViewModel`) unchanged. The route owns the open dialog (`CategoriesDialog`, saved by category ID so it survives recreation; the View dialogs were dismissed) and form errors; `CategoriesScreen` is stateless apart from the Archived toggle (`rememberSaveable`, replacing `onSaveInstanceState`). The RecyclerView, `ItemTouchHelper`, `PopupMenu`s, spinners and `MaterialAlertDialog`s became a `LazyColumn` keyed by category ID, a long-press drag (reported once on release, incoming data ignored meanwhile, as before), `DropdownMenu`s, the new shared `DropdownField`, and Compose dialogs in `CategoryDialogs.kt`. Dialog fields use the label-above `LabeledTextField` style (with a 100-character counter) instead of floating hints. The always-hidden inline "add category" form (`categoryForm`, type toggle, currency spinner) was dead UI and is not carried over; `CategoriesViewModel.addCategory` and its tests remain. Drag does not auto-scroll at the list edges; Move up/down covers long lists and accessibility. `CategoriesScreenTest` (8 tests) covers the screen and dialogs. `fragment_categories.xml`, `item_category.xml`, `dialog_income_source.xml`, `menu/income_source_actions.xml` and `CategoryAdapter` are retained pending user confirmation.
- **Transactions and Home — migrated (Phase 1, together):** they share `TransactionFeedFragment`, one layout and one ViewModel class, so splitting them would have meant keeping the XML base just for Home. `TransactionFeedFragment` hosts `TransactionFeedRoute`; `HomeFragment` and `TransactionsFragment` still each get their own `TransactionFeedViewModel`. `TransactionFeedScreen` is one `LazyColumn` (header, rows keyed by transaction ID, goals footer on Home) using the Phase 0 `SummaryCard`, `LabeledProgress`, `AmountText` and `EmptyState`. Transactions now scrolls as a whole page instead of a list fixed at 65% of the screen height inside a scroll view. Rows merge their text for TalkBack. `FeedTags` gives the title, totals, rows and filters test tags for the flow tests. `QuickAddSaveFlowTest` and `EntryValidationUndoFlowTest` were already failing before this change: they read the Transactions-only totals text while on Home. They now check the Home summary card's "Expenses: <amount>" instead. `fragment_transaction_feed.xml` and `item_transaction_refined.xml` are retained pending user confirmation.
- **Goals — migrated (Phase 1):** `GoalsFragment` hosts `GoalsRoute`, keeping the Fragment-scoped `GoalsViewModel` and the injected `AndroidGoalNotifications`/`GoalNotificationMonitor`. The notification-permission launcher became `rememberLauncherForActivityResult`, and `onResume` became `LifecycleResumeEffect`, which refreshes the prompt and rechecks the monitor when returning from the system settings. The dynamically built rows and the goal `MaterialAlertDialog` became `GoalsScreen` rows (`LabeledProgress` + update button) and `GoalDialog`; the open dialog is saved by goal ID so it survives rotation (the View dialog was dismissed). Two shared components were extracted while doing this: `FormDialog` (from the Categories dialogs; buttons and dismissal lock while saving, which Goals' Cancel button didn't before) and `SpendFlowTopBar` (now also used by Settings and Categories). `GoalCompletionNotificationFlowTest` now fills the goal form through the Compose test rule; `GoalsScreenTest` (8 tests) covers the screen and dialog. `fragment_goals.xml` and `dialog_goal.xml` are retained pending user confirmation.
- **Analytics — migrated (Phase 1):** `AnalyticsFragment` hosts `AnalyticsRoute`, keeping the Fragment-scoped `AnalyticsViewModel` (month and breakdown mode still in `SavedStateHandle`). `AnalyticsChartView` was ported directly to Compose `Canvas` (`DonutChart`, `CashFlowChart` in `AnalyticsCharts.kt`) instead of being wrapped in `AndroidView`: the drawing code was short, and the port drops its theme-attribute colour lookups. The category rows, drill-down and daily-totals dialogs became composables; the open dialog is saved so it survives recreation, and the drill-down still highlights its slice. `AnalyticsTags` gives the flow test stable handles. `AnalyticsRefinementFlowTest` was rewritten for Compose with the same checks; the "no income" assertions now read the summary card's "unavailable" announcements instead of the View's text and visibility. `AnalyticsScreenTest` (5 tests) covers the screen. `fragment_analytics.xml`, `item_analytics_category.xml` and `AnalyticsChartView` are retained pending user confirmation.
- **Quick Add — migrated, one open issue (Phase 1):** `QuickAddBottomSheet` stays a `BottomSheetDialogFragment` and hosts `QuickAddRoute` in a `ComposeView` (with nested-scroll interop so the sheet drag and the form scroll cooperate). `QuickAddViewModel` is unchanged: the form reads and writes the same `draft` `SavedStateHandle` keys through `getStateFlow`, plus a new `payment` key (the View spinner kept its own state). Chips are the Phase 0 `CategoryChip` in a `FlowRow`; spinners became `DropdownField`; the platform date/time dialogs became Material 3 `DatePickerDialog` and `TimePicker` (24-hour setting respected); the rate attribution link uses `LinkAnnotation`. `QuickAddTags` gives the flow tests stable handles; `QuickAddSaveFlowTest` and `EntryValidationUndoFlowTest` drive the form through the Compose test rule (the View `BottomSheetDialog` is not a Compose dialog, so `isDialog()` does not match it). `QuickAddScreenTest` (5 tests) covers the form. `sheet_quick_add.xml` is retained pending user confirmation.
  - **Open:** `QuickAddSaveFlowTest` asserts the save flow finishes within 10 s. On the debug build in the Pixel 8 emulator the Compose sheet is bimodal: about half the runs take 2.5–3.1 s (faster than the View sheet), the rest 11–14 s with every step uniformly 2–4× slower, so the test fails intermittently. The View sheet took 4.6–7.1 s in 7 runs on the same emulator. Not yet explained; needs investigation (e.g. a release/benchmark build and a trace of a slow run) before this screen is called done.
- **Phase 2 shell swap — done:** `MainActivity` (still an `AppCompatActivity`, so `AppCompatDelegate` night-mode switching keeps working) sets `SpendFlowShell` (`ui/main/SpendFlowShell.kt`): a `Scaffold` with the Compose `SpendFlowBottomBar` on the four tabs (and under Quick Add), the app-wide `SnackbarHost` for Quick Add's saved/undo/sync messages, and a `NavHost` padded for system bars, the bottom bar and the keyboard. `appNavGraph` in `ui/main/AppNavigation.kt` mirrors the old XML graphs with string `Routes` in nested auth/onboarding/main graphs; `showGraph`, `navigateToTab` and `openQuickAdd` keep the old back-stack rules. Screens get ViewModels with `hiltViewModel()` per back-stack entry (`hilt-navigation-compose`), so Home and Transactions still get separate feed ViewModels. Quick Add is a `dialog` destination whose window is undimmed and shows a Material 3 `ModalBottomSheet`; its ViewModel and saved draft belong to that destination. The goal-completion notification sets a flag in `MainActivity` that the shell turns into navigation to Goals. Deleted: every Fragment, `PlaceholderFragment`, `CategoryAdapter`, `AnalyticsChartView`, the four navigation graphs, `menu/income_source_actions.xml`, `activity_main.xml` and all screen/item/dialog/sheet layouts. `AppNavigationTest` now checks the same back-stack rules (plus one-entry-per-tab) against the Compose graph with placeholder screens; the flow tests use bottom-bar tab tags (`tab_<route>`), the Add button's content description and the Compose snackbar text instead of view IDs. Found and fixed while checking: the Compose bottom bar drew its nodpi PNG icons at their bitmap size; they are now 24 dp.
  - **Quick Add timing, still open:** the native Compose sheet did not fix it. `QuickAddSaveFlowTest` still lands either around 3 s or 12–19 s, so it is not caused by hosting Compose in the View `BottomSheetDialog`.
- **Next:** Phase 3 cleanup, and the Quick Add timing investigation.

The inventories below describe the initial XML baseline; migrated screens are identified explicitly.

The project contains two Activities, twelve Fragment classes (including two abstract bases and the bottom sheet), twenty main layout XML files, and ten debug layout XML files. No ViewPager usage was found. Registration is a mode of LoginFragment, not a separate Activity or Fragment. Settings is currently a placeholder.

## Activities and Fragments

Paths below are relative to `app/src/main/java/com/example/spendflow/` unless marked debug. Layout names refer to `res/layout/` in the corresponding source set.

| Class / file | Layouts | Complexity | Lists and custom Views | Existing ViewModel(s) |
| --- | --- | --- | --- | --- |
| `MainActivity.kt` | `activity_main.xml` | Complex | NavHostFragment, custom SpendFlowBottomBar, snackbar and window insets | MainViewModel; activity-scoped EntryFeedbackViewModel |
| `ui/settings/SettingsFragment.kt` | Compose: `SettingsScreen.kt`; original `fragment_placeholder.xml` retained | Simple | Material 3 top bar, Back callback and static empty state | None |
| `core/ui/PlaceholderFragment.kt` (abstract) | `fragment_placeholder.xml` | Simple | EmptyStateView; shared tab/pushed-screen presentation | None |
| `ui/onboarding/OnboardingFragment.kt` | Compose: `OnboardingScreen.kt`; original `fragment_onboarding.xml` retained | Simple | Read-only currency display and two completion callbacks | OnboardingViewModel |
| `ui/more/MoreFragment.kt` | `fragment_more.xml` | Simple | Static navigation rows; logout confirmation dialog | MoreViewModel |
| `ui/auth/LoginFragment.kt` | `fragment_login.xml` | Medium | Login/registration mode toggle; password reset; validation; autofill/IME; custom snackbar helper | LoginViewModel |
| `ui/categories/CategoriesFragment.kt` | `fragment_categories.xml`, `item_category.xml` | Medium | RecyclerView with CategoryAdapter; EmptyStateView; currency spinner and type toggle | CategoriesViewModel |
| `ui/transactions/TransactionFeedFragment.kt` (abstract) | `fragment_transaction_feed.xml`, `item_transaction_refined.xml` | Complex | RecyclerView with nested FeedAdapter; SummaryCardView, EmptyStateView, LabeledProgressView, AmountView; multiple collected flows | TransactionFeedViewModel |
| `ui/home/HomeFragment.kt` | Inherits transaction-feed layouts | Complex | Recent entries, summary and goal preview through shared base | TransactionFeedViewModel (inherited) |
| `ui/transactions/TransactionsFragment.kt` | Inherits transaction-feed layouts | Complex | History, type filters and monthly totals through shared base | TransactionFeedViewModel (inherited) |
| `ui/goals/GoalsFragment.kt` | `fragment_goals.xml`, `dialog_goal.xml` | Complex | Dynamically created goal rows and LabeledProgressView; edit/create dialog; notification permission/settings lifecycle | GoalsViewModel; injected notification service and monitor |
| `ui/analytics/AnalyticsFragment.kt` | `fragment_analytics.xml`, `item_analytics_category.xml` | Complex | SummaryCardView; two AnalyticsChartViews; dynamic category rows/progress; month navigation and drill-down dialogs | AnalyticsViewModel |
| `debug: gallery/ComponentGalleryActivity.kt` | `activity_component_gallery.xml` and nine preview layouts | Complex overall | Debug gallery exercising the shared custom component library | None |
| `ui/quickadd/QuickAddBottomSheet.kt` | `sheet_quick_add.xml` | Complex | CategoryChip instances; currency/payment spinners; conversion/rates; date/time pickers; expandable details; validation/focus and persisted draft | QuickAddViewModel; activity-scoped EntryFeedbackViewModel |

All listed ViewModels are Fragment scoped unless explicitly marked activity scoped. Preserve these owners during migration.

## Complete main layout inventory

| XML file | Complexity | Owner / purpose |
| --- | --- | --- |
| `activity_main.xml` | Complex | Activity shell, NavHost, custom bottom bar |
| `fragment_placeholder.xml` | Simple | Settings via PlaceholderFragment; custom empty state |
| `fragment_onboarding.xml` | Simple | Onboarding |
| `fragment_more.xml` | Simple | More hub |
| `fragment_login.xml` | Medium | Login, registration, password reset entry point |
| `fragment_categories.xml` | Medium | Category form, RecyclerView, empty/error states |
| `item_category.xml` | Simple | CategoryAdapter row |
| `fragment_transaction_feed.xml` | Complex | Shared Home/Transactions UI; RecyclerView and custom summary/goal components |
| `item_transaction_refined.xml` | Medium | FeedAdapter row with AmountView |
| `fragment_goals.xml` | Medium | Goal list container, status and notification controls |
| `dialog_goal.xml` | Medium | Validated goal creation/edit form |
| `fragment_analytics.xml` | Complex | Summary, custom charts and dynamic category rows |
| `item_analytics_category.xml` | Medium | Category breakdown row with progress |
| `sheet_quick_add.xml` | Complex | Entry form with conversion, categories and optional fields |
| `view_bottom_bar.xml` | Complex | SpendFlowBottomBar; navigation items and raised Add action |
| `view_summary_card.xml` | Medium | SummaryCardView; nested AmountViews |
| `view_labeled_progress.xml` | Medium | LabeledProgressView; amounts and goal progress |
| `view_labeled_switch.xml` | Simple | LabeledSwitch |
| `view_empty_state.xml` | Simple | EmptyStateView |
| `view_category_chip.xml` | Simple | CategoryChip |

Component layouts do not own ViewModels; their parent screen passes display data and actions. AmountView has no dedicated XML layout. AnalyticsChartView draws its own charts.

## Complete debug layout inventory

| XML file | Complexity | Purpose |
| --- | --- | --- |
| `activity_component_gallery.xml` | Medium | Component gallery host and preview inclusions |
| `preview_amount_view.xml` | Medium | AmountView formats and tones |
| `preview_bottom_bar.xml` | Medium | SpendFlowBottomBar |
| `preview_category_chip.xml` | Simple | CategoryChip states |
| `preview_empty_state.xml` | Simple | EmptyStateView |
| `preview_labeled_progress.xml` | Medium | LabeledProgressView states |
| `preview_labeled_switch.xml` | Simple | LabeledSwitch states |
| `preview_primary_button.xml` | Simple | Button states |
| `preview_snackbar.xml` | Simple | Snackbar styling |
| `preview_summary_card.xml` | Medium | SummaryCardView |

These layouts have no ViewModels. Keep the existing gallery as a reference while Compose components gain their own previews.

## Suggested migration order

1. **Settings — migrated**: ComposeView lifecycle, theme, static placeholder content and Fragment back callback. No new ViewModel needed. Old XML retained pending user confirmation.
2. **Onboarding — migrated**: existing currency value and completion callbacks, with the original Fragment-scoped ViewModel and auth/navigation flow.
3. **More**: navigation callbacks and logout confirmation. Preserve the existing MoreViewModel owner and MainActivity session-driven navigation.
4. **Login**: migrate login and registration together because they share one layout and ViewModel. Preserve reset-password events, field errors, submitting states, IME actions and autofill hints.
5. **Categories**: first LazyColumn, with category IDs as stable keys; preserve form/type/currency behavior and retry/error events.
6. **Transactions**: introduce stateless shared feed/item components for the history screen. Retain the XML base for Home until its own task; preserve separate Fragment ViewModel instances.
7. **Home**: reuse the feed/item composables and migrate the summary and goal preview. Keep financial calculations and formatting behavior unchanged.
8. **Goals**: migrate dynamic rows and the goal dialog while keeping notification permission launchers and settings return handling in the Fragment.
9. **Analytics**: migrate layout and category rows. Initially wrap AnalyticsChartView in AndroidView if needed; a chart rewrite is a separate task with accessibility and selection checks.
10. **Quick Add**: migrate the bottom-sheet content while retaining BottomSheetDialogFragment. Verify saved drafts, focus order, conversion, pickers, save/dismiss and activity-scoped feedback/undo behavior.
11. **Activity shell and debug gallery**: replace remaining shared components when their behavior is covered. Treat Navigation Compose as a separately requested step after the screens are stable.

For each screen, use a stateful wrapper and a stateless screen with previews. Host content with ComposeView and `DisposeOnViewTreeLifecycleDestroyed`; retain existing Fragment destinations and navigation graphs. Keep old XML and classes until the user confirms the replacement works.

## Theme and dependency setup

- `ui/theme/Color.kt`: `SpendFlowColors` light/dark palettes (all `sf_*` tokens) and the Material 3 roles derived from them. `ThemeColorsSyncTest` keeps them equal to the XML colours during coexistence.
- `ui/theme/Type.kt`: existing bundled Outfit and DM Sans files, with the current type scale and tracking. Amount-specific title/inline styles can be added when migrating AmountView.
- `ui/theme/Theme.kt`: SpendFlowTheme with system light/dark selection, an explicit darkTheme override for previews, and existing corner dimensions. Brand colors are deterministic rather than dynamic wallpaper colors.
- Version catalog: Compose BOM `2026.09.00`, Material 3, UI, tooling previews, debug tooling, activity-compose and lifecycle-runtime-compose. The Compose compiler plugin uses the existing Kotlin `2.4.21` version. AGP's existing built-in Kotlin setup is retained.
- Setup reference: [Android Compose dependencies and compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## Verification for each screen

Phase 2 verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, the full instrumented suite ran 53 tests: 52 passed and `QuickAddSaveFlowTest` failed only its 10 s time budget (14.5 s; see the open issue). The shell composable was renamed and the tab icon size fixed after that run; both were rebuilt, unit-tested and checked by hand, not re-run through the full suite. Manually checked: Login under the shell; registering reaches onboarding, Continue reaches Home, and Back from Home leaves the app; reopening lands on Home; Quick Add opens as a bottom sheet over Home with a single scrim and Back closes it; tab switching; More → Budget & categories → back arrow returns to More; Back from a tab returns to Home; logging out shows Login and Back then leaves the app. Not checked: the goal notification deep link by hand (covered by `GoalCompletionNotificationFlowTest`), large text, TalkBack.

Quick Add migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, the full instrumented suite passed once (51 tests) with the save-flow time budget met; later runs of `QuickAddSaveFlowTest` fail that budget intermittently (see the open issue above), while `EntryValidationUndoFlowTest` and `QuickAddScreenTest` pass. Manually checked: layout matches the View sheet; the empty-categories hint; Income with USD showing the rate field, attribution link and "Approx. LKR 150,000" preview; the draft (amount, currency, rate) surviving rotation; the date then time picker producing "03 Nov 2026, 16:59"; dark-mode picker buttons legible. Not checked: "Use latest rate" (external API), large text, TalkBack.

Analytics migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, the full instrumented suite passed: 46 tests, including `AnalyticsScreenTest` (5) and the rewritten `AnalyticsRefinementFlowTest`, which seeds a month of data and covers the breakdown toggle, drill-down and daily-totals dialogs, month navigation, the empty state's Add transaction, the no-income state and a dark-mode recreation. Reviewed that test's screenshots (light top, light cash flow, dark top) for layout, chart colours and the disabled next-month button. Not checked by hand: large text, TalkBack.

Goals migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, the full instrumented suite passed: 41 tests, including `GoalsScreenTest` (8) and `GoalCompletionNotificationFlowTest`. Manually checked with live data: empty state; field errors on an empty save; creating a goal ("Goal saved" snackbar); the update dialog pre-filled and kept through rotation both ways; Cancel discarding edits; updating to the target shows "· Completed" at 100%; Enable goal notifications opens the system prompt (backed out without answering) and the button then reads "Notification settings", which opens the app's notification settings and returns to Goals; dark mode. Not checked: granting the permission, write failures from the server, large text, TalkBack.

Transactions/Home migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, the full instrumented suite passed: 33 tests, including the four flow tests that drive Home and Transactions (`QuickAddSaveFlowTest`, `EntryValidationUndoFlowTest`, `AnalyticsRefinementFlowTest`, `GoalCompletionNotificationFlowTest`). Manually checked with live data: empty Home; after adding an expense and an income through Quick Add, the Home summary, recent rows and Transactions totals and rows; the Expense filter, kept across a light/dark switch; dark Transactions; See all and View goals navigation. Not checked: the goal preview with a real goal, large text, TalkBack.

Categories migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, `CategoriesScreenTest` (8, including a long-press drag) and `AppNavigationTest` (7) passed. Manually checked with live data: empty state; adding two categories (empty-name error, icon and classification dropdowns); long-press drag reorder, persisted after leaving and reopening; setting a cap and seeing it pre-filled; archive, Archived (1), restore; in dark mode, adding an income source (name error, default currency badge), the archive confirmation surviving rotation, and archiving. Not checked: write failures from the server, large text, TalkBack.

Login migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, `LoginScreenTest` (5), `AppNavigationTest` (7) and `GoalCompletionNotificationFlowTest` (1) passed. Manually checked: light log-in layout matches the XML screen; a wrong password shows "Email or password is incorrect" under the field after the keyboard Done action; Forgot password shows the success snackbar; dark create-account mode keeps typed values across the theme switch; registering a new account reaches onboarding. Not checked: real autofill with a password manager, large text, TalkBack.

More migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed (149 tests). On a Pixel 8 emulator (API 37) with the Firebase emulators, `AppNavigationTest` (7) and `GoalCompletionNotificationFlowTest` (1) passed. Manually checked in light and dark: layout matches the XML screen, the dialog survives a light/dark switch, and confirming log out returns to Login. Not checked: large text, TalkBack.

Phase 0 verification (2026-11-05): `assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin` passed with 149 tests and zero failures/errors. No screen uses the new components yet, so there is no runtime UI change; previews were compiled but not rendered or checked on a device.

Onboarding migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed with 146 tests and zero failures/errors. `git diff --check` passed. No ViewModel, repository, auth, navigation graph or dependency changes were needed. Light/dark/large-text previews are included; no device/emulator UI validation was performed. Manually check Skip/Continue completion, system Back, recreation, localized currency text, large-text scrolling and system insets.

Settings migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed with 146 tests and zero failures/errors. `git diff --check` passed. Light/dark previews are included, but no device/emulator UI validation was performed; toolbar/system Back, recreation, large text, light/dark appearance and insets still need a manual check. No new runtime dependencies were added.

Setup verification on 2026-10-28: the baseline `assembleDebug` passed. After enabling Compose, `assembleDebug testDebugUnitTest` passed with 106 tests, zero failures/errors/skips. `git diff --check` passed. No device/emulator UI validation was performed because no screen was converted. Gradle reported existing CategoryChip deprecation and test-source nullability warnings, plus native-library strip notices; none blocked the build.

Run `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows), relevant existing tests, and review the diff for ViewModel/business-logic changes. Manually check light/dark UI, keyboard and autofill, validation/errors/loading/empty states, rotation and restored drafts, back/navigation, insets and accessibility on a device or emulator. Record checks actually run; compilation alone does not confirm UI behavior.
