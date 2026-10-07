# Plan 5 — App Lock PIN & Security settings

## Outcome (2026-10-07)

Built in the app: 4-digit PIN (salted PBKDF2 hash in encrypted storage), increasing wait after wrong tries that survives restarts, fingerprint shortcut, PIN pad lock screen, Settings > Security screen (lock on/off, phone lock or app PIN, change and remove PIN, lock timeout, recovery email, hide in recent apps), email recovery through `naxits-api`.

Differences from the steps below:

- The old Data Privacy screen held only the app lock section, so the `DataPrivacy` route now opens the new Security screen and the old screen was deleted. The Settings row is relabelled Security. Plan 7 will give Security its own More entry.
- Lock method and fingerprint-shortcut preferences are in DataStore. The PIN hash, wait counters and recovery email are in an encrypted preferences file, and are not part of backups.
- Not done: the lock method and fingerprint-shortcut preferences are not exported in backups (the PIN itself never will be).
- Not tested on a device: PIN entry, the fingerprint prompt, FLAG_SECURE and the recovery flow end to end (needs the API deployed). Unit tests cover hashing, throttling, PIN storage, the recovery client and the email-code session (44 new tests in the app, 19 in `naxits-api`).

## Decisions (2026-10-07)

- **PIN:** exactly 4 digits.
- **Wrong PINs:** increasing wait (30 s after 5 failures, doubling), persisted across restarts. No data wipe.
- **Forgotten PIN:** recovery by email. The phone's own lock is not used for recovery.
- **Recent apps:** optional Security switch, off by default, sets `FLAG_SECURE`.

### Email recovery

The app has no server, and an app cannot verify an email address by itself, so this adds two endpoints to the NAX IT Solutions API (`naxits-api`, branch `feature/paisaiq-recovery`, not yet deployed):

- `POST /api/paisaiq/recovery/send` mails a six-digit code to an address.
- `POST /api/paisaiq/recovery/verify` checks the code.

Flow:

1. **Setup.** When the PIN is set, the user may add a recovery email. The app asks `send` with purpose `setup`, the user types the code, `verify` confirms, and the address is stored on the phone in encrypted storage. This proves the person owns the mailbox. Skipping is allowed but the screen says plainly that a forgotten PIN then cannot be recovered except by clearing app data.
2. **Recovery.** "Forgot PIN?" on the lock screen shows the masked address, asks for consent to send, calls `send` with purpose `recover` to the stored address only (the user cannot type a different one), takes the code, and on a verified reply lets the user set a new PIN and clears the wait.

Security properties and limits (details in `naxits-api` README): codes last 10 minutes, five wrong guesses kill a code, one code per address per minute and three per 15 minutes, durable counters in MongoDB, nothing identifying stored (keyed hashes only), rows deleted after 24 hours. The code is also bound to a random install id made on first use.

Honest limits: the check is trusted over HTTPS. Someone who controls a rooted phone and patches the app could skip the check, the same as they could remove any local lock; this protects against a thief using an ordinary phone, not a determined attacker with the device.

Privacy: this is the first network use of the PIN feature. The Security screen asks consent before the first send, `PRIVACY.md` states what the server sees (the address while mailing, hashes for 24 hours), and the feature does nothing offline.

Needs before it works end to end: deploy `naxits-api` (no new environment variable is required: the code mail is sent as `CONTACT_NAXITS_TO` through the contact form's mail settings, and the hashing key is stretched with scrypt from `MANAGE_PASSWORD` (6 or more characters), or set with the optional `RECOVERY_CODE_SECRET`, which is stronger because stretching adds length, not strength). The `CONTACT_NAXITS_TO` address must be one the mail provider is allowed to send as; with Resend that means a verified domain, otherwise it only delivers to the account owner's own address.

## Goal

Add an app-owned PIN alongside the existing biometric lock, and gather lock settings under Settings → Security.

Requirement: §7 (App Lock: PIN, biometric, lock timeout), §24 Phase 6.

## Depends on

Plan 1.

## Current state

- `$APP/data/repository/AppLockRepository.kt`: enabled flag, timeout in minutes, last-auth timestamp, `shouldLockApp()` / `shouldLockAppFlow()`. All stored in DataStore via `UserPreferencesRepository` (`APP_LOCK_ENABLED`, `APP_LOCK_TIMEOUT_MINUTES`, `LAST_AUTH_TIMESTAMP`).
- `$APP/domain/security/BiometricAuthManager.kt`: `BiometricPrompt` with biometric or device credential (the phone's own PIN / pattern).
- `$APP/presentation/ui/features/settings/applock/AppLockScreen.kt` and `AppLockViewModel.kt`: the lock screen and its logic.
- `$APP/CashiroApp.kt` navigates to the `AppLock` destination when the lock flow says so; `MainActivity.kt` holds the `AppLockViewModel`.
- There is no app-specific PIN, no Security group in `SettingsScreen.kt`, and no `FLAG_SECURE` handling.
- `androidx.security.crypto` is already a dependency; `$APP/utils/DeviceEncryption.kt` and `$APP/data/cloud/security/CloudCredentialStore.kt` show the existing encrypted-storage pattern.

## Changes

1. `$APP/data/repository/PinStore.kt`: stores a salted, slow hash of the PIN (PBKDF2-HMAC-SHA256, per-install random salt, high iteration count) in encrypted preferences, following `CloudCredentialStore`. Functions: `hasPin()`, `setPin(pin)`, `verify(pin)`, `clear()`. The PIN itself is never stored or logged.
2. Failed-attempt throttling in `PinStore`: count failures and enforce an increasing wait (for example 30 s after 5 failures, doubling). Persist the counter so restarting the app does not reset it.
3. Extend `AppLockRepository` with a lock method: `BIOMETRIC_OR_DEVICE` (today's behaviour) or `APP_PIN` with optional biometric unlock. Add preference `APP_LOCK_METHOD`.
4. Lock screen (`AppLockScreen.kt`): when the method is `APP_PIN`, show a PIN pad. Reuse `$APP/presentation/ui/features/accounts/NumberPad.kt`. Offer the biometric button when biometric is enabled and available.
5. PIN setup flow: enter, confirm, mismatch error; change PIN requires the current PIN; disabling the lock requires authentication.
6. New screen `$APP/presentation/ui/features/settings/security/SecurityScreen.kt`, destination `Security`, entry in `SettingsScreen.kt`. Contains: App Lock on/off, method, set / change PIN, biometric toggle, timeout picker (immediately, 1, 5, 15, 30 minutes), and "hide content in recent apps" which sets `FLAG_SECURE` in `MainActivity`.
7. Forgotten PIN: the only recovery is authenticating with the device credential through `BiometricAuthManager`, then setting a new PIN. State this on the setup screen.
8. Keep the existing behaviour as the default so current users are unaffected until they choose a PIN.

## Data changes

- No Room change.
- New DataStore keys: `APP_LOCK_METHOD`, `APP_LOCK_BIOMETRIC_ENABLED`, `SECURE_WINDOW_ENABLED`.
- Backup: export the lock method and timeout preferences but never the PIN hash or salt. After a restore on a new device the user sets a new PIN.

## Tests

- `PinStoreTest`: correct PIN verifies, wrong PIN fails, hash differs per salt, `clear()` removes it, throttling delays grow and persist.
- `AppLockRepositoryTest`: `shouldLockApp()` for each timeout, including 0 and a missing timestamp.
- ViewModel test for setup: mismatch rejected, change requires the old PIN.

## Verification

- Set a 4–6 digit PIN, background the app past the timeout, reopen: PIN pad appears; correct PIN unlocks, wrong PIN does not.
- Five wrong PINs trigger the wait; force-stop and reopen still shows the wait.
- With biometric enabled, fingerprint unlocks without the PIN.
- Timeout "immediately" locks on every return; "5 minutes" does not lock within 5 minutes.
- With the recent-apps option on, the app preview is blank in the task switcher.
- Existing biometric-only users still unlock as before after the update.
- Light and dark theme check of the PIN pad and Security screen.

## Done when

- [ ] PIN can be set, changed, removed and verified
- [ ] PIN stored only as a salted slow hash in encrypted storage
- [ ] Attempt throttling in place
- [ ] Settings → Security holds all lock options
- [ ] Tests pass

## Open decisions

- PIN length: fixed 4, fixed 6, or 4–6 (recommended: 4–6).
- Whether to wipe app data after many failed attempts. Recommended: no; throttling only.
