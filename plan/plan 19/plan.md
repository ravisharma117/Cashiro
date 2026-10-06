# Plan 19 — Monthly & yearly tracker

## Goal

Two overview screens: a month view centred on a Category / Budget / Spent / Remaining table with the month's full picture, and a year view with a month-by-month table, charts and a year summary.

Requirement: §16, §17.

## Depends on

Plan 11 (recurring), Plan 12 (bills and subscriptions), Plan 18 (net worth snapshots). Category figures from Plan 8.

## Current state

- `$APP/presentation/ui/features/analytics/` — `AnalyticsScreen.kt`, `AnalyticsViewModel.kt`, `AnalyticsCharts.kt`, `AnalyticsSummaryCard.kt`. Filters: `TimePeriod` (`THIS_MONTH`, `LAST_MONTH`, `CURRENT_FY`, `ALL`, `CUSTOM`), transaction type, currency. Output: total spending, category breakdown, top merchants, spending trend.
- `HomeUiState` holds this month's and last month's income / expenses.
- Budgets: `BudgetEntity` (monthly or custom period, `budget_type`, account filter) and `BudgetCategoryLimitEntity` (per-category limits); `BudgetRepository` returns `BudgetWithSpending`.
- There is no arbitrary month picker, no year picker, no calendar-year period, and no tabular month or year view.
- Charts use `compose-charts`.

## Changes

### Shared

1. `$APP/domain/usecase/GetPeriodSummaryUseCase.kt`: for any date range returns income, expenses, savings (income − expenses), savings rate, category totals, account totals, lent, borrowed, repayments. Uses the same inclusion rules as Analytics (which transaction types count as income or expense, transfers excluded, deleted excluded, currency conversion) — extract those rules from `AnalyticsViewModel` into one place and make Analytics, Home, Plan 8 and this plan all use it.
2. DAO aggregation queries in `TransactionDao.kt` grouped by month, by category and by account, so a year view is a few queries rather than loading every transaction.
3. A year basis setting: calendar year (January–December) or financial year (April–March), reusing the existing FY logic in `$APP/utils/DateRangeUtils.kt`.

### Monthly tracker

4. `$APP/presentation/ui/features/tracker/MonthlyTrackerScreen.kt` + ViewModel, with previous / next month navigation and a month picker.
5. Sections:
   - Summary: income, expenses, savings, savings rate, change versus previous month.
   - **Category table:** Category | Budget | Spent | Remaining, one row per category with a limit or with spending; over-budget rows highlighted; totals row. Budget comes from the month's `BudgetCategoryLimitEntity`; categories without a limit show a dash.
   - Budget usage: overall budget used with progress.
   - Category breakdown chart.
   - Account breakdown: spent and received per account.
   - Lending and borrowing in the month: lent, borrowed, repaid, received back.
   - Recurring payments in the month: created, upcoming, skipped (Plan 11).
   - Bills and subscriptions: paid, unpaid, total (Plan 12).
6. Rows link onward: category → category detail (Plan 8), account → account detail, bill → bill.

### Yearly tracker

7. `YearlyTrackerScreen.kt` + ViewModel with a year picker.
8. Sections:
   - **Month table:** Month | Income | Expense | Savings, twelve rows and a total; future months blank; tapping a month opens the monthly tracker for it. Compact number format (for example ₹80K) with full value on tap.
   - Year summary: income, expenses, savings, savings rate, money lent, money owed.
   - Charts: monthly income, monthly expenses, monthly savings, category spending (top categories across the year), cash flow (income versus expenses per month), lending, borrowing, net worth (from Plan 18 snapshots).

### Navigation

9. Add both under the Analysis tab as a segmented switch — Overview (today's analytics) | Month | Year — so the bottom bar stays at five items. Destinations `MonthlyTracker(yearMonth)` and `YearlyTracker(year)` for deep links from Home cards.
10. Apply Plan 6 masking to all figures and hide chart values in privacy mode.

## Data changes

None (queries and a preference for year basis; add the preference to backup).

## Tests

- `GetPeriodSummaryUseCaseTest`: income, expenses, savings, savings rate including zero income; transfers and deleted rows excluded; multi-currency; month boundaries (first and last second of the month).
- Category table: budget, spent, remaining; over budget gives a negative remaining; category with spending and no limit; limit with no spending.
- Year aggregation: twelve months including empty ones; calendar versus financial year; leap year February.
- Consistency test: the year's totals equal the sum of its months.

## Verification

- Monthly tracker for the current month: income, expenses and category totals equal the Analysis overview and the Categories tab for the same month.
- The category table matches the budget detail screen for categories with limits.
- Yearly tracker: each month row equals that month's tracker; totals equal the column sums.
- Switch year basis to financial year: months run April–March.
- An empty year and an empty month render clean empty states.
- A database with several thousand transactions opens the year view without visible delay.
- Light and dark theme check; table readable at 360dp width and with large amounts.

## Done when

- [ ] Monthly view with the category budget table and all ten listed inclusions
- [ ] Yearly view with the month table, year summary and all eight charts
- [ ] One shared set of inclusion rules across Home, Analysis, Categories and trackers
- [ ] Numbers consistent across screens
- [ ] Tests pass

## Open decisions

- Default year basis for an India-focused app: calendar year as in the requirement's example (recommended default) with financial year as an option.
- Whether the trackers replace the current Analysis overview or sit beside it (recommended: beside it).
