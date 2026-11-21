## Android app

- Before changing navigation, workout behavior, or data boundaries, read the [MVP overview](docs/mvp.md). The Android app lives in `app/`.
- For build, lint, and device tests, use the [README commands](README.md#run-checks). Instrumented tests live in `app/src/androidTest/` and use an isolated Room database.
- When changing the Room schema, keep the exported schema history in `app/schemas/`, add a migration, and follow the [APK update check](README.md#install-or-update-fitty) with data created by the previous build.

## Agent skills

### Issue tracker

Fitty tracks issues in GitHub Issues at `delaynore/fitty`. Read `docs/agents/issue-tracker.md` before reading or updating tickets.

### Triage labels

Fitty uses the five standard triage labels. Read `docs/agents/triage-labels.md` when assigning or interpreting a triage label.

### Domain docs

Fitty uses a single-context layout. Read `docs/agents/domain.md` before using domain terms or reviewing architecture decisions.
