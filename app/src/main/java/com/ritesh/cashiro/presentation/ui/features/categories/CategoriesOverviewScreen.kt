package com.ritesh.cashiro.presentation.ui.features.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.components.CategoryChip
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.YearMonth

/**
 * The default Categories view: what was spent or earned in each category this month. Adding,
 * editing and deleting categories lives behind the pencil button ([CategoriesScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoriesOverviewScreen(
    onNavigateBack: () -> Unit,
    onEditCategories: () -> Unit,
    onOpenCategory: (name: String, month: YearMonth) -> Unit,
    viewModel: CategoriesOverviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.categories),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                hasActionButton = true,
                navigationContent = { NavigationContent(onNavigateBack) },
                actionContent = {
                    IconButton(
                        onClick = onEditCategories,
                        modifier = Modifier.padding(end = 8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.categories_edit)
                        )
                    }
                }
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

            if (!state.isLoading && state.expenseRows.isEmpty() && state.incomeRows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.category_empty_month, state.month.displayName()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg)
                    )
                }
            }

            section(
                title = R.string.expense_categories,
                total = state.expenseTotal,
                currency = state.currency,
                rows = state.expenseRows,
                keyPrefix = "expense",
                onOpen = { onOpenCategory(it, state.month) }
            )
            section(
                title = R.string.income_categories,
                total = state.incomeTotal,
                currency = state.currency,
                rows = state.incomeRows,
                keyPrefix = "income",
                onOpen = { onOpenCategory(it, state.month) }
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int,
    total: java.math.BigDecimal,
    currency: String,
    rows: List<CategorySpendRow>,
    keyPrefix: String,
    onOpen: (String) -> Unit
) {
    if (rows.isEmpty()) return
    item(key = "$keyPrefix-header") {
        SectionHeader(
            title = stringResource(title),
            modifier = Modifier.padding(start = 8.dp, top = Spacing.sm),
            action = {
                Text(
                    text = CurrencyFormatter.formatCurrency(total, currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        )
    }
    items(items = rows, key = { "$keyPrefix-${it.category.id}" }) { row ->
        CategorySpendCard(row = row, currency = currency, onClick = { onOpen(row.category.name) })
    }
}

@Composable
private fun CategorySpendCard(row: CategorySpendRow, currency: String, onClick: () -> Unit) {
    val barColor = remember(row.category.color) {
        runCatching { Color(android.graphics.Color.parseColor(row.category.color)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.primary

    CashiroCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryChip(
                category = row.category,
                showText = true,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = CurrencyFormatter.formatCurrency(row.total, currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.sm)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { (row.sharePercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {}
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(
                R.string.category_share_and_count,
                row.sharePercent.toString(),
                pluralStringResource(R.plurals.category_transaction_count, row.count, row.count)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
