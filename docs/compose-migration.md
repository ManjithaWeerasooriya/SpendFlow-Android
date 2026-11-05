# SpendFlow Compose migration

## Current scope

Settings and Onboarding are migrated to Jetpack Compose with Material 3. Other screens still use XML/ViewBinding, and Fragment Navigation remains active. These migrations do not change ViewModels, repositories, Firebase, or business logic.

The goal is to remove every XML layout. The work runs in four phases:

0. **Foundations — done.** Kotlin colour tokens and the Compose component library (below).
1. **Screens inside their Fragments.** More, Login, Categories (including `dialog_income_source.xml` and `menu/income_source_actions.xml`), Transactions + Home, Goals, Analytics (chart in `AndroidView` at first), Quick Add. Port each screen's instrumented test to `compose-ui-test` in the same task.
2. **Shell swap, as one change.** `MainActivity` becomes a `ComponentActivity` with a `Scaffold` (Compose bottom bar, `SnackbarHost`, insets), Navigation Compose replaces the XML graphs, and Quick Add becomes a `ModalBottomSheet`. Add `navigation-compose` and `hilt-navigation-compose` here. All Fragments, navigation graphs, menus and `activity_main.xml` are deleted.
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
- **Next screen:** More. Continue one screen per task, using the Phase 0 components.

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

Phase 0 verification (2026-11-05): `assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin` passed with 149 tests and zero failures/errors. No screen uses the new components yet, so there is no runtime UI change; previews were compiled but not rendered or checked on a device.

Onboarding migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed with 146 tests and zero failures/errors. `git diff --check` passed. No ViewModel, repository, auth, navigation graph or dependency changes were needed. Light/dark/large-text previews are included; no device/emulator UI validation was performed. Manually check Skip/Continue completion, system Back, recreation, localized currency text, large-text scrolling and system insets.

Settings migration verification (2026-11-05): `assembleDebug testDebugUnitTest` passed with 146 tests and zero failures/errors. `git diff --check` passed. Light/dark previews are included, but no device/emulator UI validation was performed; toolbar/system Back, recreation, large text, light/dark appearance and insets still need a manual check. No new runtime dependencies were added.

Setup verification on 2026-10-28: the baseline `assembleDebug` passed. After enabling Compose, `assembleDebug testDebugUnitTest` passed with 106 tests, zero failures/errors/skips. `git diff --check` passed. No device/emulator UI validation was performed because no screen was converted. Gradle reported existing CategoryChip deprecation and test-source nullability warnings, plus native-library strip notices; none blocked the build.

Run `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows), relevant existing tests, and review the diff for ViewModel/business-logic changes. Manually check light/dark UI, keyboard and autofill, validation/errors/loading/empty states, rotation and restored drafts, back/navigation, insets and accessibility on a device or emulator. Record checks actually run; compilation alone does not confirm UI behavior.
