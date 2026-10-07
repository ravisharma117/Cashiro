package com.ritesh.cashiro.presentation.ui.features.accounts

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.domain.model.AccountKind
import com.ritesh.cashiro.domain.model.AccountOrdering
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.kind
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Lets the user drag accounts into the order used everywhere accounts are listed.
 * The new order is saved when a drag ends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderAccountsSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    accounts: List<AccountBalanceEntity>,
    hiddenKeys: Set<String>,
    onOrderChanged: (List<AccountBalanceEntity>) -> Unit
) {
    var arranged by remember { mutableStateOf(accounts) }

    LaunchedEffect(accounts) {
        if (arranged != accounts) arranged = accounts
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.reorder_accounts),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md)
            )

            Text(
                text = stringResource(R.string.reorder_accounts_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .padding(bottom = Spacing.lg)
            )

            val listState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                val list = arranged.toMutableList()
                list.add(to.index, list.removeAt(from.index))
                arranged = list
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(arranged, key = { AccountOrdering.keyOf(it) }) { account ->
                    val key = AccountOrdering.keyOf(account)
                    ReorderableItem(state = reorderableState, key = key) { isDragging ->
                        val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation.value)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            ReorderAccountRow(
                                account = account,
                                isHidden = key in hiddenKeys,
                                dragModifier = Modifier.draggableHandle(
                                    onDragStopped = { onOrderChanged(arranged) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderAccountRow(
    account: AccountBalanceEntity,
    isHidden: Boolean,
    dragModifier: Modifier
) {
    val detail = when (account.kind) {
        AccountKind.CASH -> stringResource(R.string.type_wallet)
        AccountKind.WALLET -> stringResource(R.string.type_wallet)
        AccountKind.CREDIT_CARD -> "${stringResource(R.string.type_card)} ••${account.accountLast4}"
        AccountKind.BANK -> "••${account.accountLast4}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.reorder),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragModifier
                .padding(end = 16.dp)
                .size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.bankName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isHidden) {
            Text(
                text = stringResource(R.string.account_hidden_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
