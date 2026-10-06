# Plan 16 — Startup navigator

## Goal

A guided, fully skippable first-launch setup with six pages: Welcome, Accounts, Transaction Capture (SMS), UPI, Budget, Security.

Requirement: §5.

## Depends on

Plan 3 (cash and account types), Plan 5 (PIN and biometric), Plan 14 (UPI capture). The Budget page uses existing budget code.

## Current state

- `$APP/presentation/ui/features/onboarding/OnBoardingScreen.kt` (1,555 lines), `OnBoardingViewModel.kt` (399 lines), `OnboardingState.kt`.
- Five steps driven by `currentStep`: 1 welcome → 2 SMS permission → 3 notification permission (Android 13+) → 4 accounts (SMS scan, pick main account, merge, or manual account with name / balance / last 4) → 5 profile (name, image).
- Skipping exists only for the SMS permission (`hasSkippedPermission`, `HAS_SKIPPED_SMS_PERMISSION`); Continue is disabled on step 4 until an account is chosen and on step 5 until a name is entered — setup is not optional today.
- After onboarding: sample data seeding (`IS_SAMPLE_DATA_SEEDED`), spotlight tutorial (`HAS_SHOWN_SCAN_TUTORIAL`).
- `OnBoarding` is a nav destination chosen at start in `CashiroNavHost.kt` / `CashiroApp.kt`.

## Changes

1. Restructure into pages with a pager and progress dots; every page has **Skip** (skips the page) and the first page has **Skip setup** (finishes immediately with defaults). No page blocks Next on user input.

   | Page | Content | Reuses |
   | --- | --- | --- |
   | 1 Welcome | "Your money. One simple view." Currency choice | Existing welcome art, `CurrencyBottomSheet` |
   | 2 Accounts | Add Bank account, Cash, Credit card, Wallet | `AccountType` and `ensureCashAccount` (Plan 3), existing manual-account fields |
   | 3 Transaction capture | Enable bank SMS detection: permission, then scan; notification permission asked here too | Existing steps 2–4 logic: permission request, `OptimizedSmsReaderWorker` scan, detected-account selection and merge |
   | 4 UPI | Enable UPI detection: disclosure, per-app toggles, deep link to Accessibility settings | Plan 14 settings screen pieces. Page is omitted in builds without the service |
   | 5 Budget | Set a monthly budget amount | `BudgetRepository`, `BudgetEntity` (monthly, all transactions) |
   | 6 Security | Enable App Lock: set PIN, enable biometric | Plan 5 PIN setup flow |

2. Page ordering detail: Accounts comes before SMS in the requirement. Keep that order, and when the SMS scan on page 3 finds accounts, merge them with those added on page 2 using the existing merge logic instead of asking again.
3. Profile (name, photo) moves out of the required path: optional field on Welcome or left to the Profile screen. Default name stays as today's fallback.
4. Split the 1,555-line screen into one file per page under `onboarding/pages/`, with `OnBoardingViewModel` keeping shared state; delete the per-step `when` blocks as pages replace them.
5. State: replace `currentStep: Int` with a page enum; persist the last completed page so an interrupted setup resumes.
6. Completion: one `finish()` path used by Finish, Skip setup and system back from page 1; it sets the onboarding-done flag and applies defaults (base currency, no lock, no budget).
7. Re-run: "Run setup again" in Settings opens the navigator without wiping data; pages show current values.
8. Returning from system settings (Accessibility, permission dialogs) lands back on the same page with updated status.
9. Keep the sample data and spotlight tutorial behaviour after finish; confirm the tutorial still points at the right bar items after Plan 7.
10. Strings for all pages in `values/strings.xml`.

## Data changes

- No Room change.
- DataStore: `ONBOARDING_LAST_PAGE`; reuse the existing completion flag.

## Tests

- ViewModel tests: Next and Skip on every page advance; Skip setup finishes with defaults and creates nothing; budget page saves a monthly budget only when an amount is entered; UPI page absent when the feature is unavailable; resume from persisted page.
- Accounts found by SMS scan merge with manually added ones without duplicates.

## Verification

- Fresh install, tap Skip setup: lands on Home with no accounts, no budget, no lock.
- Fresh install, complete everything: cash and bank accounts exist, SMS scan ran, UPI service prompt shown, a monthly budget exists, the app asks for the PIN on next open.
- Skip individual pages in different combinations; nothing crashes and nothing is created for skipped pages.
- Deny the SMS permission: the page explains and still allows Next.
- Kill the app on page 4 and reopen: resumes on page 4.
- Back gesture moves to the previous page; on page 1 it leaves setup.
- Light and dark theme check; small screen and landscape.

## Done when

- [ ] Six pages in the required order with the required content
- [ ] Every page skippable, plus Skip setup
- [ ] Existing SMS scan and account merge reused
- [ ] Setup can be re-run from Settings
- [ ] Tests pass

## Open decisions

- Whether to ask for the user's name at all during setup (recommended: optional field on Welcome).
- Whether sample data is still seeded when setup is skipped (recommended: keep current behaviour).
