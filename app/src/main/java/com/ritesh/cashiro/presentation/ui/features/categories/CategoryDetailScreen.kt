package com.ritesh.cashiro.presentation.ui.features.categories

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.domain.usecase.CategoryAnalysis
import com.ritesh.cashiro.domain.usecase.SubcategoryShare
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.ListItemPosition
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.components.TransactionItem
import com.ritesh.cashiro.presentation.ui.components.toShape
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharedTransitionScope.CategoryDetailScreen(
    categoryName: String,
    year: Int,
    month: Int,
    onNavigateBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onSeeAll: (name: String, month: YearMonth) -> Unit,
    animatedContentScope: AnimatedVisibilityScope,
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(categoryName, year, month) {
        viewModel.open(categoryName, YearMonth.of(year, month))
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categoriesMap by viewModel.categoriesMap.collectAsStateWithLifecycle()
    val subcategoriesMap by viewModel.subcategoriesMap.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = categoryName,
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = Spacing.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                MonthSwitcher(
                    month = state.month,
                    canGoNext = state.canGoToNextMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            }

            state.budget?.let { budget ->
                item { BudgetCard(budget) }
            }

            item {
                TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(R.string.category_tab_transactions)) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(R.string.category_tab_analysis)) }
                    )
                }
            }

            val analysis = state.analysis
            if (tab == 0) {
                if (state.transactions.isEmpty()) {
                    item {
                        EmptyMonth(
                            month = state.month,
                            lastMonthTotal = analysis?.previousTotal,
                            currency = state.currency
                        )
                    }
                } else {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.transactions_count, state.transactions.size),
                            modifier = Modifier.padding(start = 8.dp),
                            action = {
                                TextButton(onClick = { onSeeAll(categoryName, state.month) }) {
                                    Text(stringResource(R.string.category_see_all))
                                }
                            }
                        )
                    }
                    itemsIndexed(items = state.transactions, key = { _, t -> t.id }) { index, transaction ->
                        val categoryEntity = categoriesMap[transaction.category]
                        val subcategoryEntity = if (categoryEntity != null && transaction.subcategory != null) {
                            subcategoriesMap[transaction.subcategory]
                        } else null
                        val shape = ListItemPosition.from(index, state.transactions.size).toShape()

                        this@CategoryDetailScreen.TransactionItem(
                            transaction = transaction,
                            categoryEntity = categoryEntity,
                            subcategoryEntity = subcategoryEntity,
                            convertedAmount = state.convertedAmounts[transaction.id],
                            mainCurrency = state.currency,
                            onClick = { onOpenTransaction(transaction.id) },
                            shape = shape,
                            sharedElementKey = "transaction_${transaction.id}",
                            animatedContentScope = animatedContentScope
                        )
                    }
                }
            } else if (analysis != null) {
                analysisItems(analysis, state.currency, state.isIncome)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.analysisItems(
    analysis: CategoryAnalysis,
    currency: String,
    isIncome: Boolean
) {
    item { SummaryCard(analysis, currency, isIncome) }
    item { ComparisonCard(analysis, currency, isIncome) }
    item { ShareCard(analysis, isIncome) }
    item { TrendCard(analysis, currency) }
    if (analysis.subcategories.isNotEmpty()) {
        item {
            SectionHeader(
                title = stringResource(R.string.category_subcategories),
                modifier = Modifier.padding(start = 8.dp, top = Spacing.sm)
            )
        }
        items(analysis.subcategories.size) { index ->
            SubcategoryRow(analysis.subcategories[index], currency)
        }
    }
}

@Composable
private fun SummaryCard(analysis: CategoryAnalysis, currency: String, isIncome: Boolean) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(if (isIncome) R.string.category_total_earned else R.string.category_total_spent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyFormatter.formatCurrency(analysis.total, currency),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    stringResource(R.string.category_daily_average),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    CurrencyFormatter.formatCurrency(analysis.dailyAverage, currency),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.transactions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(analysis.count.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ComparisonCard(analysis: CategoryAnalysis, currency: String, isIncome: Boolean) {
    val change = analysis.change
    val percent = analysis.changePercent
    val text = when {
        analysis.previousTotal.signum() == 0 -> stringResource(R.string.category_change_none)
        change.signum() == 0 -> stringResource(R.string.category_change_same)
        change.signum() > 0 -> stringResource(
            R.string.category_change_up,
            CurrencyFormatter.formatCurrency(change.abs(), currency),
            percent.toString()
        )
        else -> stringResource(
            R.string.category_change_down,
            CurrencyFormatter.formatCurrency(change.abs(), currency),
            percent?.let { kotlin.math.abs(it) }.toString()
        )
    }
    // More spending is a warning; more income is good.
    val goodWhenUp = isIncome
    val tone = when {
        change.signum() == 0 || analysis.previousTotal.signum() == 0 -> MaterialTheme.colorScheme.onSurface
        (change.signum() > 0) == goodWhenUp -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.category_vs_last_month),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text, style = MaterialTheme.typography.titleMedium, color = tone)
    }
}

@Composable
private fun ShareCard(analysis: CategoryAnalysis, isIncome: Boolean) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(if (isIncome) R.string.category_share_of_income else R.string.category_share_of_spending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(
                R.string.category_share_value,
                analysis.shareOfTotalPercent.toString(),
                stringResource(R.string.categories)
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { (analysis.shareOfTotalPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun TrendCard(analysis: CategoryAnalysis, currency: String) {
    val max = analysis.trend.maxOfOrNull { it.total } ?: BigDecimal.ZERO
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.category_trend),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            analysis.trend.forEach { point ->
                val fraction = if (max.signum() > 0) {
                    (point.total.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val selected = point.month == analysis.month
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((88 * fraction).dp.coerceAtLeast(2.dp))
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = point.month.month.getDisplayName(
                            java.time.format.TextStyle.SHORT_STANDALONE,
                            java.util.Locale.getDefault()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = CurrencyFormatter.formatCurrency(max, currency),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubcategoryRow(item: SubcategoryShare, currency: String) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name ?: stringResource(R.string.category_no_subcategory),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                CurrencyFormatter.formatCurrency(item.total, currency),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { (item.percentOfCategory / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {}
        )
    }
}

@Composable
private fun BudgetCard(budget: CategoryBudgetInfo) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.category_budget_limit),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(
                R.string.category_budget_used,
                CurrencyFormatter.formatCurrency(budget.spent, budget.currency),
                CurrencyFormatter.formatCurrency(budget.limit, budget.currency)
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { budget.usedFraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (budget.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {}
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (budget.isOver) {
                stringResource(
                    R.string.category_budget_over,
                    CurrencyFormatter.formatCurrency(budget.remaining.abs(), budget.currency)
                )
            } else {
                stringResource(
                    R.string.category_budget_left,
                    CurrencyFormatter.formatCurrency(budget.remaining, budget.currency)
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (budget.isOver) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyMonth(month: YearMonth, lastMonthTotal: BigDecimal?, currency: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.category_empty_month, month.displayName()),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        if (lastMonthTotal != null && lastMonthTotal.signum() > 0) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(
                    R.string.category_empty_last_month,
                    CurrencyFormatter.formatCurrency(lastMonthTotal, currency)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
