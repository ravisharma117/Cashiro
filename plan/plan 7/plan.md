# Plan 7 — Bottom navigation & More

## Goal

Restructure the main navigation to **Home | Transactions | Categories | Analysis | More**, with a More screen listing the secondary destinations.

Requirement: §10 (bottom navigation), §26 (product navigation).

## Depends on

Plan 1.

## Current state

- `$APP/presentation/navigation/BottomNavItem.kt` defines three items: Home, Analytics, Transactions.
- `$APP/presentation/navigation/CashiroBottomNavigation.kt` (line ~109) builds the bar from `listOf(BottomNavItem.Home, BottomNavItem.Analytics, BottomNavItem.Transactions)` and supports several bar styles (`NavigationBarStyle.kt`), hidden labels and a pill indicator.
- `CashiroDestinations.kt` already has `Categories`, `Settings`, `Budgets`, `Subscriptions`, `LendBorrow`, `ManageAccounts`, `DataPrivacy`, `CloudBackup` and others.
- `Categories` is a management screen (`$APP/presentation/ui/features/categories/CategoriesScreen.kt`) reached from Settings.
- Secondary screens are reached from `SettingsScreen.kt` (`onNavigateTo…` callbacks), the Home widgets and the Profile screen.
- A spotlight tutorial (`$APP/presentation/ui/features/spotlight/`, `components/SpotlightTutorial.kt`) points at current bar positions.
- The AI chat (`Chat` destination) is reachable from the current UI and must stay reachable.

## Changes

1. `BottomNavItem.kt`: add `Categories` and `More` items; order the list Home, Transactions, Categories, Analysis, More. Rename the Analytics label to "Analysis" (string only; keep the `Analytics` destination and class names). Icons: `Iconax.Category2` for Categories, `Iconax.Menu` for More.
2. `CashiroBottomNavigation.kt`: use the five-item list. Check each `NavigationBarStyle` renders five items without clipping at small widths and with labels hidden; adjust item spacing rather than font size.
3. New destination `More` and screen `$APP/presentation/ui/features/more/MoreScreen.kt` with grouped rows, in the requirement's order:
   - Accounts → `ManageAccounts`
   - Budgets → `Budgets`
   - Recurring Transactions → placeholder row until Plan 11
   - Subscriptions → `Subscriptions`
   - Lending & Borrowing → `LendBorrow`
   - Savings Goals → placeholder row until Plan 17
   - Import / Export → `DataPrivacy`
   - Reports → placeholder row until Plan 20
   - Security → `Security` (Plan 5) or `AppLock` settings if Plan 5 is not merged
   - Backup → `CloudBackup`
   - Settings → `Settings`
   
   Rows for features not yet built are hidden, not shown disabled; each later plan adds its row.
4. `CashiroNavHost.kt`: register `More` as a top-level route with the same transitions and back behaviour as the other tabs (back from a tab returns to Home, then exits).
5. Remove duplicates from `SettingsScreen.kt` that now live in More (accounts, budgets, lend / borrow, categories), keeping Settings for preferences: appearance, currency, notifications, SMS, rules, webhooks, about, developer.
6. Keep AI chat reachable: a row in More and the existing Home entry point.
7. Categories tab: for this plan it opens the existing `CategoriesScreen`. Plan 8 turns it into the category-centred view.
8. Update the spotlight tutorial targets to the new positions.
9. Tablets: `CLAUDE.md` calls for a NavigationRail. If a rail exists, give it the same five items; if not, leave as is and note it.

## Data changes

None.

## Tests

- Unit test on the nav item list: five items in the required order with unique routes.
- Existing tests pass.

## Verification

- Bar shows Home, Transactions, Categories, Analysis, More in that order in every navigation bar style, with and without labels.
- Each More row opens the right screen and back returns to More.
- Tab state is kept when switching tabs (scroll position on Transactions).
- Back from any tab goes to Home; back on Home exits.
- Quick-settings tiles and app shortcuts (`AddTransactionTileService` etc.) still open Add Transaction.
- The spotlight tutorial highlights the right items on a fresh install.
- Light and dark theme check of the bar and More screen, on a small (360dp) and a large screen.

## Done when

- [ ] Five-tab bar in place across all bar styles
- [ ] More screen lists every existing secondary destination
- [ ] No destination became unreachable (walk the Plan 1 checklist)
- [ ] Settings trimmed of moved entries
- [ ] Tests pass

## Open decisions

- Whether the central add button / FAB stays where it is with five tabs (recommended: keep, verify it does not overlap the bar).
- Whether Profile stays as its own screen from the Home header (recommended: yes).
