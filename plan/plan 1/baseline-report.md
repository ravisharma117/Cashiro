# Plan 1 — Baseline report

Date: 2026-10-06

## Summary

| Step | Result |
| --- | --- |
| 1. `upstream` remote | Done |
| 2. `develop` branch | Done — `main` synced with upstream; `main` and `develop` pushed |
| 3. Build both flavors | **Blocked** — no JDK or Android SDK on this machine |
| 4. Test and lint baseline | **Blocked** — same reason |
| 5. Feature checklist on a device | **Not done** — needs the APK from step 3 |
| 6. License check | Done |
| 7. `CLAUDE.md` corrected | Done |
| 8. CI runs app unit tests | Workflow edited; unverified until it runs on GitHub |

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
- The Plan 1 edits (`CLAUDE.md`, `.github/workflows/test.yml`, `plan/`) are uncommitted in the working tree on `develop`.

## 3–4. Build, test, lint

Not run. The machine has:

- no `java` on `PATH`, no `JAVA_HOME`
- no Android SDK (`ANDROID_HOME` / `ANDROID_SDK_ROOT` unset; nothing under `%LOCALAPPDATA%\Android`)
- no Android Studio
- no `local.properties` in the repository

What the build needs:

- JDK 17 (CI uses Temurin 17; the app targets JVM 11 bytecode)
- Android SDK with platform 36 and matching build-tools (`compileSdk = 36`)
- `local.properties` with `sdk.dir=…`. `RSA_PUBLIC_KEY` and the release keystore entries are optional: the build falls back to an empty key and debug builds need no keystore.
- Gradle 8.13 is fetched by the wrapper.

Commands to run once the tools are installed:

```
./gradlew :app:assembleStandardDebug :app:assembleFdroidDebug
./gradlew test
./gradlew lint
```

Record the results here, including failures that exist before any feature work.

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

Unverified until the workflow runs on GitHub, which needs `develop` pushed.

## Open items to close Plan 1

1. Choose `dev` or `develop` as the integration branch and delete the other; fast-forward `main` to upstream if wanted; push the branches to `origin`.
2. Install JDK 17 and the Android SDK, then run the build, tests and lint and fill in sections 3–4.
3. Walk the feature checklist on a device and fill in section 5.
4. Confirm the new CI job passes on its first run.
