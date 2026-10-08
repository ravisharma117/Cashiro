package com.ritesh.cashiro.presentation.ui.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.domain.service.MonthlySavings
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.features.categories.displayName
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** This month's income, expenses and what was kept. Totals follow the hide-totals setting. */
@Composable
fun MonthlySummaryCard(
    income: BigDecimal,
    expenses: BigDecimal,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = remember(income, expenses) { MonthlySavings.of(income, expenses) }
    CashiroCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_widget_monthly_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = YearMonth.now().displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(stringResource(R.string.income), summary.income, currency, MaterialTheme.colorScheme.primary)
            Figure(stringResource(R.string.expense), summary.expenses, currency, MaterialTheme.colorScheme.error)
            Figure(
                label = stringResource(R.string.home_savings),
                amount = summary.savings,
                currency = currency,
                color = if (summary.savings.signum() < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                note = stringResource(R.string.home_savings_rate, summary.ratePercent.toPlainString())
            )
        }
    }
}

@Composable
private fun Figure(
    label: String,
    amount: BigDecimal,
    currency: String,
    color: androidx.compose.ui.graphics.Color,
    note: String? = null
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            CurrencyFormatter.formatTotal(amount, currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The next few recurring transactions that are due. */
@Composable
fun RecurringHomeCard(
    items: List<RecurringTransactionEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    CashiroCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Text(
            text = stringResource(R.string.home_widget_recurring),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.nextRunDate.format(formatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    CurrencyFormatter.formatCurrency(item.amount, item.currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
