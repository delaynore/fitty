## Android app

- Before changing navigation, workout behavior, or data boundaries, read the [MVP overview](docs/mvp.md). The Android app lives in `app/`.
- Before adding or changing an Android test, use the [Fitty test-layer rules](docs/android-development.md#run-checks). The same section covers local builds, lint, and device checks. Instrumented tests use an isolated Room database.
- When changing the Room schema, keep the exported schema history in `app/schemas/`, add a migration, and follow the [APK update check](docs/android-development.md#install-or-update-fitty) with data created by the previous build.
- When opening or updating a PR, check its changed paths against the [Android Smoke workflow](.github/workflows/android-smoke.yml). If Smoke is required, add the `android-smoke` label when opening the PR or add it to the existing PR before handing it off.

## Agent skills

### Issue tracker

Fitty tracks issues in GitHub Issues at `delaynore/fitty`. Read `docs/agents/issue-tracker.md` before reading or updating tickets.

### Triage labels

Fitty uses the five standard triage labels. Read `docs/agents/triage-labels.md` when assigning or interpreting a triage label.

### Domain docs

Fitty uses a single-context layout. Read `docs/agents/domain.md` before using domain terms or reviewing architecture decisions.
