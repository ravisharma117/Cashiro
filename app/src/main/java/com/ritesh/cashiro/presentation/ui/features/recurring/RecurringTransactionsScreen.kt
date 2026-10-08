package com.ritesh.cashiro.presentation.ui.features.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.OccurrenceStatus
import com.ritesh.cashiro.data.database.entity.RecurrenceFrequency
import com.ritesh.cashiro.data.database.entity.RecurrenceUnit
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecurringTransactionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecurringTransactionsViewModel = hiltViewModel()
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val subcategories by viewModel.subcategoriesMap.collectAsStateWithLifecycle()
    val baseCurrency by viewModel.baseCurrency.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var historyFor by remember { mutableStateOf<RecurringTransactionEntity?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.recurring_title),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.recurring_add)) }
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
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                CashiroCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.recurring_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (list.isEmpty) {
                item {
                    Text(
                        text = stringResource(R.string.recurring_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.lg)
                    )
                }
            }
            scheduleSection(R.string.recurring_section_active, list.active, viewModel, { editing = it; showForm = true }, { deleting = it }, { historyFor = it })
            scheduleSection(R.string.recurring_section_paused, list.paused, viewModel, { editing = it; showForm = true }, { deleting = it }, { historyFor = it })
            scheduleSection(R.string.recurring_section_ended, list.ended, viewModel, { editing = it; showForm = true }, { deleting = it }, { historyFor = it })
        }
    }

    if (showForm) {
        ModalBottomSheet(
            onDismissRequest = { showForm = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            RecurringEditForm(
                schedule = editing,
                accounts = accounts,
                categories = categories,
                subcategoriesMap = subcategories,
                defaultCurrency = baseCurrency,
                onSave = viewModel::save,
                onDismiss = { showForm = false }
            )
        }
    }

    deleting?.let { schedule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.recurring_delete_title, schedule.title)) },
            text = { Text(stringResource(R.string.recurring_delete_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(schedule.id); deleting = null }) {
                    Text(stringResource(R.string.recurring_menu_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.recurring_cancel)) }
            }
        )
    }

    historyFor?.let { schedule ->
        val history by viewModel.history(schedule.id).collectAsStateWithLifecycle(initialValue = emptyList())
        val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
        ModalBottomSheet(onDismissRequest = { historyFor = null }) {
            Column(
                modifier = Modifier.padding(horizontal = Dimensions.Padding.content).padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.recurring_history_title) + ": " + schedule.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (history.isEmpty()) {
                    Text(stringResource(R.string.recurring_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                history.forEach { occurrence ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(occurrence.dueDate.format(formatter))
                        Text(
                            text = stringResource(
                                when (occurrence.status) {
                                    OccurrenceStatus.CREATED -> R.string.recurring_status_created
                                    OccurrenceStatus.SKIPPED -> R.string.recurring_status_skipped
                                    OccurrenceStatus.PENDING -> R.string.recurring_status_pending
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.scheduleSection(
    title: Int,
    schedules: List<RecurringTransactionEntity>,
    viewModel: RecurringTransactionsViewModel,
    onEdit: (RecurringTransactionEntity) -> Unit,
    onDelete: (RecurringTransactionEntity) -> Unit,
    onHistory: (RecurringTransactionEntity) -> Unit
) {
    if (schedules.isEmpty()) return
    item(key = "header-$title") {
        SectionHeader(title = stringResource(title), modifier = Modifier.padding(start = 8.dp, top = Spacing.sm))
    }
    items(items = schedules, key = { it.id }) { schedule ->
        ScheduleCard(
            schedule = schedule,
            onClick = { onEdit(schedule) },
            onSkipNext = { viewModel.skipNext(schedule.id) },
            onPause = { viewModel.pause(schedule.id) },
            onResume = { viewModel.resume(schedule.id) },
            onHistory = { onHistory(schedule) },
            onDelete = { onDelete(schedule) }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: RecurringTransactionEntity,
    onClick: () -> Unit,
    onSkipNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val repeat = repeatText(schedule)
    val status = when (schedule.state) {
        RecurringState.ACTIVE -> stringResource(R.string.recurring_next, schedule.nextRunDate.format(formatter))
        RecurringState.PAUSED -> stringResource(R.string.recurring_paused_label)
        RecurringState.ENDED -> stringResource(R.string.recurring_ended_label)
    }

    CashiroCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.recurring_summary, repeat, status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = CurrencyFormatter.formatCurrency(schedule.amount, schedule.currency),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )
            Column {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (schedule.state == RecurringState.ACTIVE) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recurring_menu_skip_next)) },
                            onClick = { menuOpen = false; onSkipNext() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recurring_menu_pause)) },
                            onClick = { menuOpen = false; onPause() }
                        )
                    }
                    if (schedule.state == RecurringState.PAUSED) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recurring_menu_resume)) },
                            onClick = { menuOpen = false; onResume() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recurring_menu_history)) },
                        onClick = { menuOpen = false; onHistory() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recurring_menu_delete)) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

/** "Monthly", "Every 2 weeks", ... */
@Composable
private fun repeatText(schedule: RecurringTransactionEntity): String {
    val interval = schedule.intervalCount.coerceAtLeast(1)
    val unit = when (schedule.frequency) {
        RecurrenceFrequency.DAILY -> RecurrenceUnit.DAY
        RecurrenceFrequency.WEEKLY -> RecurrenceUnit.WEEK
        RecurrenceFrequency.MONTHLY -> RecurrenceUnit.MONTH
        RecurrenceFrequency.YEARLY -> RecurrenceUnit.YEAR
        RecurrenceFrequency.CUSTOM -> schedule.customUnit ?: RecurrenceUnit.DAY
    }
    if (schedule.frequency != RecurrenceFrequency.CUSTOM) {
        return stringResource(frequencyLabel(schedule.frequency))
    }
    val plural = when (unit) {
        RecurrenceUnit.DAY -> R.plurals.recurring_every_days
        RecurrenceUnit.WEEK -> R.plurals.recurring_every_weeks
        RecurrenceUnit.MONTH -> R.plurals.recurring_every_months
        RecurrenceUnit.YEAR -> R.plurals.recurring_every_years
    }
    return pluralStringResource(plural, interval, interval)
}
