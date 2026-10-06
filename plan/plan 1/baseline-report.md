# Plan 1 — Baseline report

Date: 2026-10-06

## Summary

| Step | Result |
| --- | --- |
| 1. `upstream` remote | Done |
| 2. `develop` branch | Done — `main` synced with upstream; `main` and `develop` pushed |
| 3. Build both flavors | Done — both debug flavors build |
| 4. Test and lint baseline | Done — recorded below: one flaky unit test, 233 existing lint errors |
| 5. Feature checklist on a device | **Open** — the owner will walk it on a phone |
| 6. License check | Done |
| 7. `CLAUDE.md` corrected | Done |
| 8. CI runs app unit tests | Done — `app-tests` and `parser-tests` both passed on `develop` |

## 1. Remotes

- `origin` → `https://github.com/ravisharma117/Cashiro.git`
- `upstream` → `https://github.com/ritesh-kanwar/Cashiro.git` (added and fetched)

## 2. Branches and upstream sync

- The fork's `main` (`bdf7ab6a`) is 0 commits ahead of and 6 behind `upstream/main` (`40a387fd`). The 6 upstream commits touch 25 files (chat error mapping, backup importers, add-transaction and subscription flows, PDF / SMS reading performance, dependency updates). A fast-forward applies them with no merge commit and no conflicts.
- `main` was fast-forwarded to `upstream/main` and pushed.
- `develop` was created at the same commit and pushed, tracking `origin/develop`. It is the integration branch.
- A stray local `dev` branch that appeared during this work was deleted; it pointed at the same commit, so nothing was lost.
- State after this step: `main`, `develop`, `origin/main`, `origin/develop` and `upstream/main` are all at `40a387fd`.
- Version is unchanged by the sync: 2.1.61-beta, `versionCode` 94.
- The Plan 1 edits (`CLAUDE.md`, `.github/workflows/test.yml`, `plan/`) and the APK copy step are committed and pushed on `develop`.

## 3–4. Build, test, lint

### Environment set up on this machine

The machine had no JDK, Android SDK or Android Studio. Installed:

- Temurin JDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`), via `winget`
- Android SDK at `%LOCALAPPDATA%\Android\Sdk`: command-line tools, platform-tools, `platforms;android-36`, `build-tools;36.0.0`
- `local.properties` (gitignored) with `sdk.dir`, plus the personal APK copy settings

Two problems had to be fixed before the build passed:

| Problem | Cause | Fix |
| --- | --- | --- |
| `java.io.IOException: Invalid file path` | `sdk.dir` written with single backslashes | Use forward slashes in `local.properties` |
| `OutOfMemoryError: GC overhead limit exceeded` in the Kotlin compile | Both flavors compiling in parallel in a 2 GB compiler process, on a 16 GB machine with about 1 GB free | Build one flavor per invocation and pass `-Pkotlin.daemon.jvmargs=-Xmx4g` |

`gradle.properties` was not changed. The working commands on this machine:

```
./gradlew :app:assembleStandardDebug -Pkotlin.daemon.jvmargs=-Xmx4g
./gradlew :app:assembleFdroidDebug   -Pkotlin.daemon.jvmargs=-Xmx4g
```

### Results

Tests and lint were run for the `standard` debug variant only. The blanket `./gradlew test` and `./gradlew lint` also compile both release variants, which this machine does not have the memory for. CI runs the same scope.

| Check | Command | Result |
| --- | --- | --- |
| Build `standard` debug | `:app:assembleStandardDebug` | Pass — five APKs (four per-architecture plus universal) |
| Build `fdroid` debug | `:app:assembleFdroidDebug` | Pass — one APK |
| Parser tests | `:parser-core:test` | Pass — 395 tests, 0 failures |
| App unit tests | `:app:testStandardDebugUnitTest` | 158 tests, **1 flaky failure** (see below) |
| Lint | `:app:lintStandardDebug` | **Fails** — 233 errors, 1,877 warnings, 6 hints |

Release builds were not attempted (they need the release keystore).

### Known issues that exist before any feature work

**Flaky unit test.** `AddTransactionUseCaseTest` › `undoing CREDIT delete re-applies the balance` fails intermittently with `expected:<30000> but was:<25000>`. It failed in the full run, then passed 2 of 3 times when the class was rerun alone, and passed in CI. The cause was not investigated. Treat a failure of this one test as pre-existing, and fix or stabilise it before Plan 10, which relies on this test class to pin current behaviour.

**Lint errors.** All 233 are in upstream code. By message, the largest groups are:

- Translated strings whose format arguments do not match the English string (`duration_minutes_seconds`, `duration_seconds`, `batch_progress_format`) — about 59
- Plural strings with a `one` quantity that is wrong for the locale — about 24
- Reading resource values through `LocalContext.current` in composables — 22

Most of the errors come from the Crowdin-managed translation files, not from Kotlin code. Because lint fails today, it cannot gate merges as it stands. Options: add a lint baseline file so only new errors fail, or fix the translation format errors at source. Reports are in `app/build/reports/` (not checked in).

### APK output

A post-build step in `app/build.gradle.kts` copies the built APK to a personal folder with the version in the file name, for example `Cashiro-standard-universal-debug-v2.1.61-beta.apk`. It is opt-in through `APK_COPY_DIR` and `APK_COPY_INCLUDE` in `local.properties` and does nothing when those are absent.

For a phone, use the universal or `arm64-v8a` APK. The `x86` APK is for emulators and does not install on a phone.

## 5. Feature verification checklist

To be walked on a device with the `standard` debug APK. Mark each ✔ / ✘ with a note.

- [ ] Onboarding completes with and without SMS permission
- [ ] SMS scan imports transactions; a new incoming SMS creates one
- [ ] Manual add: expense, income, transfer, subscription
- [ ] Accounts: add, edit, merge, detail, balance history
- [ ] Categories and subcategories: add, edit, delete
- [ ] Budgets: create, detail, history
- [ ] Analytics: each time period and type filter
- [ ] Rules: create, apply to past transactions
- [ ] Subscriptions list and upcoming notifications
- [ ] Lend / borrow: person, entry, partial settlement, settle up
- [ ] Search and filters on Transactions
- [ ] Backup export, import, Cashew import, PDF statement import
- [ ] App lock with biometric and timeout
- [ ] Multiple currencies and currency settings
- [ ] Light and dark theme, dynamic color

## 6. License check

This project is AGPL-3.0 (inherited from upstream Cashiro). Licenses read from each repository on 2026-10-06.

| Repository | License | Copy code into this project? |
| --- | --- | --- |
| `ritesh-kanwar/Cashiro` (base) | AGPL-3.0 | Yes — it is the base. Keep copyright and license notices; the app must stay AGPL-3.0 with source available. |
| `GreenIcePhoenix/TraceLedger` | Custom "All Rights Reserved" — source is for viewing and reference only; redistribution, modification and derivative works are prohibited without written permission | **No.** Do not copy or adapt its code. Recurring transactions (Plan 11) and CSV import (Plan 13) must be written independently. |
| `xxwarwolfxx/UPI-Expense-Tracker` | GPL-3.0 | Yes. GPL-3.0 section 13 allows combining with AGPL-3.0 code. Keep its copyright notices on any copied file and note the origin. |
| `nkuppan/expensemanager` | Apache-2.0 | Yes. Apache-2.0 code may be included in an AGPL-3.0 project. Keep its copyright and license notices and any `NOTICE` content. |

Consequences for later plans:

- Plans 11 and 13 treat TraceLedger as a feature checklist only. Reading its source closely and then re-implementing the same structure is the risky middle ground; work from the requirement and this project's own patterns instead. If direct reuse is wanted, ask the author for written permission first.
- Plan 14 may reuse UPI Expense Tracker code with attribution.
- Plans 3–9 may reuse Expense Manager code with attribution.
- Any copied file gets an origin note, and third-party attributions are listed in the app's licenses screen.

This table records what the license files say. It is not legal advice.

## 7. `CLAUDE.md`

Corrected:

- Version: was 2.1.3 (code 13), now 2.1.61-beta (code 94), pointing at `app/build.gradle.kts` as the source of truth.
- Scaffold: `PennyWiseScaffold` does not exist in the source. Screens use Material 3 `Scaffold` with `CustomTitleTopAppBar`.
- Current phase: now points at `plan/README.md`.
- Added the branch workflow and the per-flavor build commands.

Still stale and left alone: the "Supported Banks (44 parsers)" list and the parser package paths were not audited.

## 8. CI

`.github/workflows/test.yml`:

- Triggers now include `develop` for pushes and pull requests.
- New job `app-tests` runs `./gradlew :app:testStandardDebugUnitTest --continue` and uploads the results.

Verified: on `develop` at `e7239988`, both `parser-tests` and `app-tests` completed successfully.

## Open items to close Plan 1

1. Walk the feature checklist in section 5 on a phone (owner).

Carried forward, not blocking Plan 1:

- Stabilise the flaky `AddTransactionUseCaseTest` case before Plan 10.
- Lint: decided on 2026-10-06 to leave the 233 existing errors as they are. Lint is not a merge gate; check new code for lint errors by reading the report, not by the task's exit status.
