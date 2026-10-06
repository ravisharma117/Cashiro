# Plan 17 — Savings goals

## Goal

Let users create savings goals with a target, track what is saved and what remains, and see progress and the monthly contribution.

Requirement: §19.

## Depends on

Plan 9 (the `SAVINGS_GOALS` Home widget placeholder). Plan 7 for the More row.

## Current state

- Nothing exists for goals: no entity, screen or destination.
- Patterns to follow: the lend / borrow feature is the closest shape (a parent with a ledger of entries) — `LendBorrowPersonEntity` / `LendBorrowTransactionEntity`, `LendBorrowRepository`, `features/lendborrow/`.
- Reusable UI: `BudgetCard.kt` (progress), `ColorPickerDialog.kt`, `categories/IconSelector.kt`, `accounts/NumberPad.kt`, `add/AmountInput.kt`, `DatePicker.kt`.
- Budgets already support a savings-style type (`BudgetType`), which is a period target, not a goal with a running balance — keep them separate.

## Changes

1. Entity `SavingsGoalEntity` (`savings_goals`): id, name, target amount, currency, target date (nullable), icon name, color, linked account key (nullable), state (`ACTIVE`, `ACHIEVED`, `ARCHIVED`), display order, notes, timestamps, `is_sample`.
2. Entity `SavingsContributionEntity` (`savings_contributions`): goal id (foreign key, cascade), amount (positive = contribution, negative = withdrawal), date, note, transaction id (nullable). Index on goal id and date.
3. `SavingsGoalDao`, `SavingsGoalRepository`, Hilt wiring. Repository exposes `GoalWithProgress`: saved (sum of contributions), remaining (`max(target − saved, 0)`), percentage, this month's contribution, average monthly contribution, and when a target date exists, the required monthly amount to finish on time and whether the goal is on track.
4. Use cases in `$APP/domain/usecase/SavingsGoalUseCases.kt`: create / edit / archive / delete goal, add contribution, withdraw, mark achieved (automatic when saved ≥ target).
5. Contributions and real money:
   - A contribution may optionally create a transaction: a transfer to the linked account, or an `INVESTMENT`-type entry, through `TransactionEngine`. Default: no transaction — the goal just earmarks money.
   - An existing transaction can be assigned to a goal from transaction detail ("Add to goal").
6. UI in `$APP/presentation/ui/features/goals/`:
   - `SavingsGoalsScreen`: active goals as cards with progress bar, saved / target, remaining; achieved and archived sections; total saved across goals.
   - `GoalDetailScreen`: header with target, saved, remaining; monthly contribution chart (`compose-charts`); required monthly amount and on-track status; contribution history with edit / delete.
   - Add / edit goal sheet and add-contribution sheet.
   - Destinations `SavingsGoals`, `GoalDetail(goalId)`; row in More.
7. Home `SAVINGS_GOALS` widget: top goals with progress; flip the availability flag set in Plan 9.
8. Apply Plan 6 privacy masking to goal amounts (as balances).
9. Optional reminder: a monthly nudge to contribute, through the existing daily reminder path, off by default.

## Data changes

- Migration (next free number): create `savings_goals` and `savings_contributions`.
- Backup: add both lists to `BackupModels`, exporter and importer.

## Tests

- Repository / use-case tests: saved and remaining after contributions and a withdrawal; remaining never negative; auto-achieve at target; this-month and average contribution; required monthly amount with and without target date, including a target date in the past.
- Deleting a goal removes its contributions and leaves linked transactions intact.

## Verification

- Create "New Laptop" with target ₹100,000 and add ₹35,000: shows saved ₹35,000, remaining ₹65,000, 35%.
- Create "Emergency Fund" ₹300,000 with ₹150,000 saved: 50%.
- Add contributions in two different months: monthly chart and average reflect them.
- Withdraw part: saved decreases.
- Reach the target: goal marked achieved and moved to its section.
- The Home widget shows goals and opens the goal on tap.
- Export / import backup restores goals and contributions.
- Light and dark theme check.

## Done when

- [ ] Goals with target, saved, remaining and progress
- [ ] Contributions, withdrawals and history
- [ ] Monthly contribution shown, with required monthly amount when a date is set
- [ ] In More and on the Home widget
- [ ] Backup updated; tests pass

## Open decisions

- Whether goal savings are "earmarked" inside existing account balances (recommended; simple, no double counting in net worth) or held in a separate virtual account.
