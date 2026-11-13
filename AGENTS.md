# Compose Migration Rules

- Migrate XML layouts to Jetpack Compose with Material 3 one screen per task, only when requested.
- Do not change ViewModels, repositories, Firebase code, or business logic unless explicitly asked.
- Give each migrated screen a stateful wrapper using the existing ViewModel and a stateless composable accepting state and callbacks. Add a preview using sample state without Firebase or a ViewModel.
- Collect UI state with collectAsStateWithLifecycle; handle one-time events with lifecycle-aware collection so navigation and feedback are not replayed by recomposition.
- Replace the migrated UI's ViewBinding/findViewById usage with Compose state and callbacks. Retain ViewBinding for screens still using Views.
- Host Compose in the existing Fragment using ComposeView and DisposeOnViewTreeLifecycleDestroyed. Keep Fragment navigation, destination IDs, ViewModel scopes, deep links, and arguments unchanged until navigation migration is explicitly requested.
- Preserve validation, loading/error/empty states, saved drafts, keyboard and autofill behavior, accessibility, insets, back navigation, and existing auth flows.
- Do not delete old XML layouts or Activity/Fragment classes until the user confirms that the replacement works.
- Keep user-facing strings in strings.xml. Use SpendFlowTheme for colors, typography, and shapes; preserve light and dark appearance and existing font resources.
- Use stable keys for LazyColumn items and callbacks for user actions. AndroidView is acceptable as a temporary bridge for custom Views.
- Keep changes small and run .\gradlew.bat assembleDebug after each migration (./gradlew assembleDebug on Unix). Run relevant existing tests and report any verification blocked by the environment.
- See docs/compose-migration.md for the initial inventory and suggested order. Update it as screens are migrated.
