# Plan 9 — Home widgets completion

## Goal

Complete the customisable Home screen: add the cards the requirement lists that do not exist yet, so the user can select and reorder all of them.

Requirement: §9.

## Depends on

Plan 4 (account order used by the Accounts card), Plan 7 (navigation targets for card taps).

## Current state

Select, hide and drag-reorder already work.

- `$APP/data/preferences/HomeWidget.kt`: `NETWORTH_SUMMARY`, `LOANS`, `ACCOUNT_CAROUSEL`, `UPCOMING_SUBSCRIPTIONS`, `RECENT_TRANSACTIONS`, `BUDGET_CAROUSEL`, `TRANSACTION_HEATMAP`. Display names are hardcoded English strings in the enum.
- Order and hidden set: `HOME_WIDGETS_ORDER`, `HIDDEN_HOME_WIDGETS` in `UserPreferencesRepository`.
- `$APP/presentation/ui/features/home/EditWidgetsSheet.kt`: reorder sheet. `NETWORTH_SUMMARY` is pinned first and excluded from reordering (`HomeViewModel.kt` ~158–167).
- `HomeScreen.kt` (~438) renders each widget in a `when`; `HomeUiState` already carries month income, expenses, last-month figures, budgets, lend / borrow summary.

Mapping to the requirement's list:

| Requirement card | Today |
| --- | --- |
| Total Balance | `NETWORTH_SUMMARY` |
| Monthly Income / Expenses / Savings | Data in state, no dedicated card |
| Recent Transactions | `RECENT_TRANSACTIONS` |
| Accounts | `ACCOUNT_CAROUSEL` |
| Budgets | `BUDGET_CAROUSEL` |
| Categories | Missing |
| Subscriptions | `UPCOMING_SUBSCRIPTIONS` |
| Recurring Transactions | Missing (feature arrives in Plan 11) |
| Lending / Borrowing | `LOANS` |
| Savings Goals | Missing (feature arrives in Plan 17) |

## Changes

1. Add enum values `MONTHLY_SUMMARY`, `TOP_CATEGORIES`, `RECURRING`, `SAVINGS_GOALS` to `HomeWidget`. Replace `displayName: String` with a `@StringRes` so names are translatable; update `EditWidgetsSheet` accordingly.
2. `MONTHLY_SUMMARY` card (`$APP/presentation/ui/components/MonthlySummaryCard.kt`): month name, income, expenses, savings (income − expenses) and savings rate, from existing `HomeUiState` fields. Tap opens Analysis for this month.
3. `TOP_CATEGORIES` card: top 4–5 spending categories this month with amount and share bar. Reuse the category totals query from Plan 8 if merged, otherwise the Analytics breakdown. Tap opens the Categories tab; tapping a row opens that category.
4. `RECURRING` and `SAVINGS_GOALS`: register the enum values and a per-widget availability flag so they are not offered in the edit sheet until their features exist. Plans 11 and 17 supply the card content and flip the flag.
5. Make widget handling tolerant of unknown and new values: when reading the stored order, ignore names that no longer exist and append new widgets at their `defaultOrder` (visible by default for `MONTHLY_SUMMARY`, hidden by default for `TOP_CATEGORIES`, so existing users' Home does not change unexpectedly).
6. Allow hiding Total Balance: remove the special pinning only if the user chooses to (see Open decisions).
7. Apply the Plan 6 privacy masking to the new cards if Plan 6 is merged.
8. Move per-widget data loading in `HomeViewModel` behind visibility: do not query data for hidden widgets.

## Data changes

- No Room change. Existing DataStore keys are reused; new enum names appear in them.
- Backup: already carries `order` / `hidden`; confirm import ignores unknown names.

## Tests

- `HomeWidgetOrderTest`: stored order with an unknown name is tolerated; new widgets are appended at default position; hidden defaults apply only to new widgets.
- ViewModel test: monthly savings and savings rate, including zero income.

## Verification

- Edit widgets sheet lists the new cards; enabling, disabling and dragging them changes Home immediately and persists after restart.
- An existing install upgrading sees its previous layout unchanged, plus Monthly Summary.
- Monthly Summary figures equal the Analysis tab for the same month.
- Recurring and Savings Goals do not appear in the sheet yet.
- Backup export / import restores the layout.
- Light and dark theme check.

## Done when

- [ ] Monthly Summary and Top Categories cards available and reorderable
- [ ] Placeholders registered for Recurring and Savings Goals, hidden until built
- [ ] Widget names translatable
- [ ] Upgrade keeps existing layouts
- [ ] Tests pass

## Open decisions

- Whether Total Balance may be moved or hidden like the other cards. The requirement lists it as selectable; today it is pinned first. Recommended: make it reorderable and hideable.
- Whether Income, Expenses and Savings are one card (recommended, matches the requirement's example layout) or three separate cards.
