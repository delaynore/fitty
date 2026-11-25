# Fitty coding standards

Reviewers check changes against these judgement calls alongside the automated boundary tests (`ArchitectureBoundaryTest`) and Android Lint (`:app:lintDebug`).

## Architecture and package boundaries

- Keep Android-free business rules and value types in `domain/` (`docs/mvp.md`). Avoid leaving transitional `typealias` shims in `ui/` when moving types to `domain/`.
- Keep Android system adapters (including `SharedPreferences` stores, clocks, foreground services, and `PackageInstaller` wrappers) in `platform/`.
- In `data/` repositories and update services, wrap Room calls in `runStorageOperation` (mapping `SQLiteException` to `StorageException`), enforce multi-step invariants in `withTransaction`, and keep network/file I/O (e.g., `GitHubUpdateService`, `UpdateApkCache`) on `Dispatchers.IO` with typed error results and cancellation cleanup.
- Prefer normal primary-constructor DI (with production defaults where useful) and composition-root seams (`FittyApplication`) over test-only secondary constructors, `@VisibleForTesting`, `Noop*`/empty fakes, or test helper APIs in `app/src/main/` (`compose-ui-testing-patterns/SKILL.md`); keep test doubles in test source sets (`app/src/test/`, or `app/src/androidTest/` only for instrumented seams).
- When a change updates or conflicts with an ADR in `docs/adr/`, commit the ADR update in the same change (`docs/agents/domain.md`).

## State, events, and Compose effects

- Keep `UiState` pure: do not leak internal tracking fields or duplicate `Boolean` flags that repeat what `pendingEvent` or domain data already expresses (`docs/adr/0002-screen-state-and-text-input.md`).
- Keep one-off UI events in a single nullable `pendingEvent` (`docs/adr/0002-screen-state-and-text-input.md`) only while events are mutually exclusive; when multiple screens share a `ViewModel`, each screen must filter `pendingEvent` to the events it owns before passing it to `UiEventEffect`. If independent concurrent sources can emit overlapping events, split ownership or queue them and update the ADR.
- Key `LaunchedEffect` by the semantic inputs that should restart it (including `lifecycle` whenever the effect suspends on it so it never captures a stale `LifecycleOwner`), react to lifecycle transitions via `repeatOnLifecycle`/`withStarted` or observed `currentStateAsState()`/`currentStateFlow` (never by reading `lifecycle.currentState` directly), exclude high-frequency progress ticks when they should not restart the effect, and wrap changing lambdas with `rememberUpdatedState` (`.agents/skills/compose-state-and-effects/references/side-effects.md`).
- Never swallow `CancellationException` in suspending code (including `catch (RuntimeException)`/`catch (Exception)`/`catch (Throwable)` — always rethrow it). In `Service` coroutines using `collectLatest`, treat cancellation separately from normal completion or errors so cancelling an older signal does not call `stopSelfResult` on a newly arrived `startId`.
