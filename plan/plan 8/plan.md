# Plan 8 — Category transactions + analysis

## Goal

Make the Categories tab useful for reviewing and understanding spending: a category list with totals, and a category detail showing both its transactions and an analysis.

Requirement: §10 (Categories screen: Transactions and Analysis).

## Depends on

Plan 7 (the Categories tab).

## Current state

- `$APP/presentation/ui/features/categories/CategoriesScreen.kt` (705 lines) and `CategoriesViewModel.kt` manage categories and subcategories: add, edit, delete, icons. No spending figures.
- Categories are stored by name: `TransactionEntity.category` is a string, not a foreign key. `CategoryEntity` has `display_order`, `is_income`, color and icon.
- `Transactions` destination accepts `category`, `period`, `type` filters, so a filtered list already exists.
- `$APP/presentation/ui/features/analytics/AnalyticsViewModel.kt` already computes a category breakdown (`CategoryData`: amount, percentage, count) and a spending trend (`BalancePoint`), with currency conversion and `TimePeriod` filters from `$APP/presentation/common/Filters.kt`.
- Charts in `analytics/AnalyticsCharts.kt` use `compose-charts`.

## Changes

1. `$APP/domain/usecase/GetCategoryAnalysisUseCase.kt`: for a category and month, returns
   - month total and transaction count
   - daily average (total ÷ days elapsed in the month, or days in month for past months)
   - previous-month total, absolute and percentage change
   - share of the month's total spending
   - trend: totals for the last 6 months
   - subcategory split
   
   Use the same transaction-type rules and currency conversion as `AnalyticsViewModel` so numbers match the Analysis tab; extract the shared calculation rather than duplicating it.
2. DAO queries in `TransactionDao.kt`: sum by category for a date range, monthly sums for one category over N months. Exclude deleted rows and the same types Analytics excludes (transfers, balance updates).
3. Categories tab landing (`CategoriesScreen.kt`): each category row shows this month's spend and a thin share bar, sorted by `display_order`. A month selector at the top. Expense and Income sections. Move the management actions (add, edit, reorder, delete) behind an "Edit categories" action so the default view is for browsing.
4. New destination `CategoryDetail(categoryName, yearMonth)` and screen `$APP/presentation/ui/features/categories/CategoryDetailScreen.kt` + `CategoryDetailViewModel.kt` with two tabs:
   - **Transactions:** the category's transactions for the selected month, reusing `TransactionItem`; tapping opens `TransactionDetail`; a "See all" action opens `Transactions(category = …)`.
   - **Analysis:** monthly total, daily average, previous-month comparison, 6-month trend chart, share of total spending, subcategory split.
5. From Analysis tab's category breakdown, tapping a category opens `CategoryDetail` (today it opens the filtered transaction list).
6. If the category has a budget limit (`BudgetCategoryLimitEntity`), show limit, spent and remaining on the detail header.
7. Empty states: a category with no transactions this month shows last month's figure and a clear empty message.

## Data changes

None (queries only).

## Tests

- `GetCategoryAnalysisUseCaseTest` with fixed data: total, daily average for current and past month, previous-month comparison including a zero previous month, share of total, 6-month trend with gaps, multi-currency conversion.
- DAO query test or fake-based test confirming deleted and transfer transactions are excluded.

## Verification

- Category totals on the Categories tab equal the Analysis tab's breakdown for the same month.
- Opening Food shows its transactions; the Analysis tab shows total, daily average, comparison to last month, trend chart and percentage of total.
- Changing the month updates list and analysis.
- Editing a transaction's category moves it between categories immediately.
- "Edit categories" still supports everything the old screen did.
- Light and dark theme check; long category names and large amounts do not clip.

## Done when

- [ ] Categories tab shows per-category spend for a selectable month
- [ ] Category detail has Transactions and Analysis tabs with all five analysis figures
- [ ] Numbers agree with the Analysis tab
- [ ] Category management still fully available
- [ ] Tests pass

## Open decisions

- Whether income categories get the same detail view (recommended: yes, same screen, "earned" wording).
