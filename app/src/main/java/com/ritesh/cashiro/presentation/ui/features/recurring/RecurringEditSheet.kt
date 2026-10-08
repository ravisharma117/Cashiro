package com.ritesh.cashiro.presentation.ui.features.recurring

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.CategoryEntity
import com.ritesh.cashiro.data.database.entity.RecurrenceFrequency
import com.ritesh.cashiro.data.database.entity.RecurrenceUnit
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.data.database.entity.SubcategoryEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.presentation.ui.components.CategorySelectionSheet
import com.ritesh.cashiro.presentation.ui.components.DatePicker
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class DateTarget { START, END }

private val REMINDER_CHOICES = listOf(0, 1, 2, 3, 5, 7)
private val EXPENSE_AND_INCOME = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

/** The add / edit form. Pass [schedule] to edit, or null for a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditForm(
    schedule: RecurringTransactionEntity?,
    accounts: List<AccountBalanceEntity>,
    categories: List<CategoryEntity>,
    subcategoriesMap: Map<Long, List<SubcategoryEntity>>,
    defaultCurrency: String,
    onSave: (RecurringTransactionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(schedule?.title.orEmpty()) }
    var amountText by remember { mutableStateOf(schedule?.amount?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var type by remember { mutableStateOf(schedule?.transactionType ?: TransactionType.EXPENSE) }
    var category by remember { mutableStateOf(schedule?.category) }
    var subcategory by remember { mutableStateOf(schedule?.subcategory) }
    var accountKey by remember {
        mutableStateOf(schedule?.let { s -> s.bankName?.let { it to s.accountLast4.orEmpty() } })
    }
    var frequency by remember { mutableStateOf(schedule?.frequency ?: RecurrenceFrequency.MONTHLY) }
    var intervalText by remember { mutableStateOf((schedule?.intervalCount ?: 1).toString()) }
    var customUnit by remember { mutableStateOf(schedule?.customUnit ?: RecurrenceUnit.MONTH) }
    var startDate by remember { mutableStateOf(schedule?.startDate ?: LocalDate.now()) }
    var endDate by remember { mutableStateOf(schedule?.endDate) }
    var autoCreate by remember { mutableStateOf(schedule?.autoCreate ?: true) }
    var reminderDays by remember { mutableStateOf(schedule?.reminderDaysBefore ?: 0) }
    var notes by remember { mutableStateOf(schedule?.notes.orEmpty()) }

    var accountMenuOpen by remember { mutableStateOf(false) }
    var reminderMenuOpen by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf<DateTarget?>(null) }
    var pickingCategory by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val amount = amountText.replace(',', '.').toBigDecimalOrNull()
    val nameOk = title.isNotBlank()
    val amountOk = amount != null && amount.signum() > 0
    val categoryOk = category != null
    val datesOk = endDate?.let { !it.isBefore(startDate) } ?: true
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val interval = if (frequency == RecurrenceFrequency.CUSTOM) intervalText.toIntOrNull()?.coerceIn(1, 999) ?: 1 else 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensions.Padding.content)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = stringResource(if (schedule == null) R.string.recurring_new_title else R.string.recurring_edit_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.recurring_field_name)) },
            isError = showErrors && !nameOk,
            supportingText = if (showErrors && !nameOk) {
                { Text(stringResource(R.string.recurring_error_name)) }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text(stringResource(R.string.recurring_field_amount)) },
            isError = showErrors && !amountOk,
            supportingText = if (showErrors && !amountOk) {
                { Text(stringResource(R.string.recurring_error_amount)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            EXPENSE_AND_INCOME.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = {
                        Text(
                            stringResource(
                                if (option == TransactionType.EXPENSE) R.string.expense else R.string.income
                            )
                        )
                    }
                )
            }
        }

        // Category
        OutlinedButton(onClick = { pickingCategory = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = listOfNotNull(category, subcategory).joinToString(" › ")
                    .ifEmpty { stringResource(R.string.recurring_field_category) },
                modifier = Modifier.weight(1f)
            )
        }
        if (showErrors && !categoryOk) {
            Text(
                stringResource(R.string.recurring_error_category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Account
        Column {
            OutlinedButton(onClick = { accountMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                val selected = accountKey
                Text(
                    text = selected?.let { (bank, last4) -> "$bank ••$last4" }
                        ?: stringResource(R.string.recurring_account_none),
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recurring_account_none)) },
                    onClick = { accountKey = null; accountMenuOpen = false }
                )
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text("${account.bankName} ••${account.accountLast4}") },
                        onClick = {
                            accountKey = account.bankName to account.accountLast4
                            accountMenuOpen = false
                        }
                    )
                }
            }
        }

        // Repeat rule
        Text(stringResource(R.string.recurring_repeat_label), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            RecurrenceFrequency.entries.forEach { option ->
                FilterChip(
                    selected = frequency == option,
                    onClick = { frequency = option },
                    label = { Text(stringResource(frequencyLabel(option))) }
                )
            }
        }
        if (frequency == RecurrenceFrequency.CUSTOM) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.recurring_custom_every))
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.padding(0.dp).weight(0.4f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RecurrenceUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = customUnit == unit,
                        onClick = { customUnit = unit },
                        label = { Text(stringResource(unitLabel(unit))) }
                    )
                }
            }
        }

        // Dates
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { pickingDate = DateTarget.START }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.recurring_field_start) + ": " + startDate.format(formatter))
            }
            OutlinedButton(onClick = { pickingDate = DateTarget.END }, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recurring_field_end) + ": " +
                        (endDate?.format(formatter) ?: stringResource(R.string.recurring_end_never))
                )
            }
        }
        if (endDate != null) {
            TextButton(onClick = { endDate = null }) { Text(stringResource(R.string.recurring_end_never)) }
        }
        if (showErrors && !datesOk) {
            Text(
                stringResource(R.string.recurring_error_dates),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Automatic or remind-only
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.recurring_auto_create), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.recurring_auto_create_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = autoCreate, onCheckedChange = { autoCreate = it })
        }

        // Reminder
        Column {
            OutlinedButton(onClick = { reminderMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.recurring_reminder_label) + ": " + reminderText(reminderDays),
                    modifier = Modifier.weight(1f)
                )
            }
            DropdownMenu(expanded = reminderMenuOpen, onDismissRequest = { reminderMenuOpen = false }) {
                REMINDER_CHOICES.forEach { days ->
                    DropdownMenuItem(
                        text = { Text(reminderText(days)) },
                        onClick = { reminderDays = days; reminderMenuOpen = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.recurring_field_notes)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.recurring_cancel))
            }
            Button(
                onClick = {
                    showErrors = true
                    if (nameOk && amountOk && categoryOk && datesOk) {
                        val base = schedule
                        onSave(
                            (base ?: RecurringTransactionEntity(
                                title = "", amount = BigDecimal.ZERO, category = "",
                                startDate = startDate, nextRunDate = startDate
                            )).copy(
                                title = title.trim(),
                                amount = amount!!,
                                currency = base?.currency ?: accountCurrency(accounts, accountKey, defaultCurrency),
                                transactionType = type,
                                category = category!!,
                                subcategory = subcategory,
                                bankName = accountKey?.first,
                                accountLast4 = accountKey?.second,
                                frequency = frequency,
                                intervalCount = interval,
                                customUnit = if (frequency == RecurrenceFrequency.CUSTOM) customUnit else null,
                                startDate = startDate,
                                endDate = endDate,
                                autoCreate = autoCreate,
                                reminderDaysBefore = reminderDays,
                                notes = notes.trim().ifEmpty { null }
                            )
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.recurring_save)) }
        }
    }

    pickingDate?.let { target ->
        val initial = if (target == DateTarget.START) startDate else (endDate ?: startDate)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePicker(
            onDismiss = { pickingDate = null },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    if (target == DateTarget.START) startDate = picked else endDate = picked
                }
                pickingDate = null
            },
            datePickerState = state
        )
    }

    if (pickingCategory) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { pickingCategory = false }) {
            Column(modifier = Modifier.padding(bottom = Spacing.xxl)) {
                CategorySelectionSheet(
                    categories = categories.filter { it.isIncome == (type == TransactionType.INCOME) },
                    subcategoriesMap = subcategoriesMap,
                    onSelectionComplete = { picked, sub ->
                        category = picked.name
                        subcategory = sub?.name
                        pickingCategory = false
                    },
                    onDismiss = { pickingCategory = false }
                )
            }
        }
    }
}

@Composable
private fun reminderText(days: Int): String =
    if (days == 0) stringResource(R.string.recurring_reminder_none)
    else pluralStringResource(R.plurals.recurring_reminder_days, days, days)

private fun accountCurrency(
    accounts: List<AccountBalanceEntity>,
    key: Pair<String, String>?,
    fallback: String
): String = accounts.firstOrNull { it.bankName == key?.first && it.accountLast4 == key.second }?.currency ?: fallback

internal fun frequencyLabel(frequency: RecurrenceFrequency): Int = when (frequency) {
    RecurrenceFrequency.DAILY -> R.string.recurring_freq_daily
    RecurrenceFrequency.WEEKLY -> R.string.recurring_freq_weekly
    RecurrenceFrequency.MONTHLY -> R.string.recurring_freq_monthly
    RecurrenceFrequency.YEARLY -> R.string.recurring_freq_yearly
    RecurrenceFrequency.CUSTOM -> R.string.recurring_freq_custom
}

internal fun unitLabel(unit: RecurrenceUnit): Int = when (unit) {
    RecurrenceUnit.DAY -> R.string.recurring_unit_day
    RecurrenceUnit.WEEK -> R.string.recurring_unit_week
    RecurrenceUnit.MONTH -> R.string.recurring_unit_month
    RecurrenceUnit.YEAR -> R.string.recurring_unit_year
}
