# Plan 1 — Baseline & branching

## Goal

Establish a clean, verified starting point before any feature work: branches, remotes, a passing build for both flavors, a recorded check of existing features, and a license decision for the reference repositories.

Requirement: §3 (initial fork tasks), §24 Phase 0, §25.

## Depends on

Nothing.

## Current state

- `origin` → `https://github.com/ravisharma117/Cashiro.git`. Only `main` exists locally and remotely.
- No `upstream` remote pointing at `ritesh-kanwar/Cashiro`.
- `LICENSE` is AGPL-3.0.
- Version 2.1.61-beta, `versionCode` 94, `compileSdk` / `targetSdk` 36, `minSdk` 26.
- Flavors `fdroid` and `standard` (dimension `version`), each with its own `app/src/<flavor>/java`.
- CI: `.github/workflows/test.yml` runs only `./gradlew :parser-core:test`; `release.yml` builds releases.
- Tests: 19 unit tests in `app/src/test`, parser tests in `parser-core/src/test`, one placeholder instrumented test.
- `CLAUDE.md` is out of date: it says version 2.1.3 and refers to a `PennyWiseScaffold` composable that does not exist in the source.

## Changes

1. Add the upstream remote: `git remote add upstream https://github.com/ritesh-kanwar/Cashiro.git`, then `git fetch upstream`. Record how far `main` is from `upstream/main`.
2. Create `develop` from `main` and push it. Feature branches in later plans branch from `develop`.
3. Build both flavors in debug: `./gradlew :app:assembleStandardDebug :app:assembleFdroidDebug`. Fix only what blocks the build (SDK path, JDK version, missing local properties); note each fix.
4. Run `./gradlew test` and `./gradlew lint`. Record failures that exist before any change, so later plans are not blamed for them.
5. Install the `standard` debug APK and walk the checklist below. Record results in `plan/plan 1/baseline-report.md`.
6. License check: read the license file of each reference repository and record in the report whether its code may be copied into an AGPL-3.0 project, or used as reference only.
   - `GreenIcePhoenix/TraceLedger`
   - `xxwarwolfxx/UPI-Expense-Tracker`
   - `nkuppan/expensemanager`
7. Update `CLAUDE.md`: current version, the real scaffold / top bar component used by screens, and the `develop` branch workflow.
8. Extend `.github/workflows/test.yml` to also run `./gradlew :app:testStandardDebugUnitTest` so app tests gate merges.

### Feature verification checklist (step 5)

- Onboarding completes with and without SMS permission
- SMS scan imports transactions; a new incoming SMS creates one
- Manual add: expense, income, transfer, subscription
- Accounts: add, edit, merge, detail, balance history
- Categories and subcategories: add, edit, delete
- Budgets: create, detail, history
- Analytics: each time period and type filter
- Rules: create, apply to past transactions
- Subscriptions list and upcoming notifications
- Lend / borrow: person, entry, partial settlement, settle up
- Search and filters on Transactions
- Backup export, import, Cashew import, PDF statement import
- App lock with biometric and timeout
- Multiple currencies and currency settings
- Light and dark theme, dynamic color

## Data changes

None.

## Tests

No new tests. The output is the recorded baseline of existing test and lint results.

## Verification

- `git branch -a` shows `develop` locally and on `origin`; `git remote -v` shows `upstream`.
- Both debug APKs build.
- `baseline-report.md` exists with build result, test result, lint summary, checklist results and the license table.
- CI runs app unit tests on a pull request to `develop`.

## Done when

- [ ] `upstream` remote added and fetched
- [ ] `develop` created and pushed
- [ ] Both flavors build
- [ ] Test and lint baseline recorded
- [ ] Feature checklist walked on a device
- [ ] License table filled for the three reference repositories
- [ ] `CLAUDE.md` corrected
- [ ] CI runs app unit tests

## Open decisions

- Whether to merge `upstream/main` into the fork now, if it is ahead. Recommended: yes, before feature work, since conflicts only grow.
- Whether `main` stays as the release branch with `develop` as integration (as §25 describes). Recommended: yes.
