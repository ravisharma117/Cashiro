# Plan 2 — Rebrand

## Goal

Give the application its own name, application ID and icon so it installs alongside upstream Cashiro and is clearly a separate product.

Requirement: §3 (application name, package ID, icon, branding), §24 Phase 0.

## Depends on

Plan 1.

## Current state

- `app/build.gradle.kts`: `namespace = "com.ritesh.cashiro"`, `applicationId = "com.ritesh.cashiro"`.
- `settings.gradle.kts`: `rootProject.name = "cashiro-beta"`.
- `app/src/main/res/values/strings.xml`: `app_name` = "Cashiro".
- `AndroidManifest.xml`:
  - Provider authorities already use `${applicationId}` (startup, FileProvider).
  - Two hardcoded intent actions: `com.ritesh.cashiro.ACTION_DELETE_TRANSACTION` and `com.ritesh.cashiro.ACTION_CONFIRM_TRANSACTION` (receiver `NotificationActionReceiver`).
  - Five launcher `activity-alias` entries for alternate icons (`MainActivityOriginal`, `Anarchy`, `Zenith`, `Monochrome`, `Comic`), switched by `$APP/utils/IconSwitchingUtils.kt` and `$APP/data/preferences/AppIcon.kt`.
- Icons under `app/src/main/res/mipmap-*` and `drawable*`.
- Upstream-specific links (Discord, GitHub, donation) in `$APP/presentation/ui/features/settings/about/AboutScreen.kt` and related icon files.
- Play in-app update / review libraries in the `standard` flavor.
- `fastlane/` metadata and `README.md` describe upstream.

## Changes

1. Decide name and ID (see Open decisions).
2. Change `applicationId` only. Keep `namespace` and the Kotlin package `com.ritesh.cashiro`: renaming the package touches all 435 source files and makes every future upstream merge conflict.
3. Update `app_name` in `values/strings.xml`. Leave translated `app_name` values to Crowdin, or set `translatable="false"` on it so one value applies everywhere (recommended).
4. Replace the two hardcoded action strings in the manifest and in `NotificationActionReceiver` (and wherever the intents are built) with a constant derived from the application ID, so they cannot drift again.
5. Replace launcher icons. Keep the alias mechanism; either supply new art for each alias or reduce to one default icon and remove unused aliases together with their `AppIcon` entries.
6. Update About screen links, support links and any update-check URL to the fork's repository. Remove or replace donation links that belong to the upstream author.
7. Keep the upstream copyright and license notices (AGPL-3.0 requires it). Add a line crediting upstream Cashiro in About and `README.md`.
8. Update `README.md`, `fastlane/` metadata, `rootProject.name`.
9. Search for remaining upstream identifiers: `grep -rn "com.ritesh.cashiro" app/src/main/AndroidManifest.xml app/src/main/res` and `grep -rni "ritesh-kanwar" .`.

## Data changes

None. A new `applicationId` means a fresh install with an empty database; existing data moves through backup export / import.

## Tests

No new unit tests. Existing tests must still pass.

## Verification

- `./gradlew :app:assembleStandardDebug :app:assembleFdroidDebug` builds.
- The new app installs next to an existing upstream Cashiro install without a signature or ID conflict.
- Launcher shows the new name and icon; switching app icon in Appearance still works.
- A transaction notification's Confirm and Delete actions still work (covers the renamed intent actions).
- Export a backup and share it (covers the FileProvider authority).
- Light and dark theme check of About.

## Done when

- [ ] New `applicationId` and app name in place
- [ ] No hardcoded upstream ID left in manifest or resources
- [ ] New icon(s) shown; icon switching works or is removed cleanly
- [ ] About, README and store metadata point at the fork and credit upstream
- [ ] Both flavors build and install

## Open decisions

- **App name** — must be chosen before starting.
- **Application ID** — for example `com.naxits.<name>`; must be chosen before starting.
- **Icon art** — who supplies it, and whether to keep five alternates or one.
- **Play services features** — keep in-app update / review in the `standard` flavor only if the app will be published on Google Play under the new ID.
