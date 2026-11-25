# Fitty coding standards

Reviewers check changes against these judgement calls alongside the automated boundary tests (`ArchitectureBoundaryTest`) and Android Lint (`:app:lintDebug`).

## Architecture and package boundaries

- Keep Android-free business rules and value types in `domain/` (`docs/mvp.md`). Avoid leaving transitional `typealias` shims in `ui/` when moving types to `domain/`.
- Keep Android system adapters (including `SharedPreferences` stores, clocks, foreground services, and `PackageInstaller` wrappers) in `platform/`.
- Never add test-only constructors, `Noop*` fakes, or helper APIs to production classes (`compose-ui-testing-patterns/SKILL.md`); keep test doubles in `app/src/test/`.
- When a change updates or conflicts with an ADR in `docs/adr/`, commit the ADR update in the same change (`docs/agents/domain.md`).

## State, events, and Compose effects

- Keep `UiState` pure: do not leak internal tracking fields or duplicate `Boolean` flags that repeat what `pendingEvent` or domain data already expresses (`docs/adr/0002-screen-state-and-text-input.md`).
- When multiple screens share a `ViewModel`, each screen must filter `pendingEvent` to the events it owns before passing it to `UiEventEffect`, so one screen never consumes another screen's event.
- Key `LaunchedEffect` by the semantic inputs that should restart it (including `lifecycle` when reading `lifecycle.currentState`), exclude high-frequency progress ticks when they should not restart the effect, and wrap changing lambdas with `rememberUpdatedState` (`.agents/skills/compose-state-and-effects/references/side-effects.md`).
- In `Service` coroutines using `collectLatest`, distinguish `CancellationException` from normal completion or errors so cancelling an older signal does not `stopSelfResult` on a newly arrived `startId`.
