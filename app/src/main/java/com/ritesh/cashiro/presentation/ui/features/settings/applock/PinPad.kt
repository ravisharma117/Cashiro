package com.ritesh.cashiro.presentation.ui.features.settings.applock

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.data.security.PinHasher

/** The row of dots showing how many digits have been typed. Never shows the digits. */
@Composable
fun PinDots(
    filled: Int,
    modifier: Modifier = Modifier,
    length: Int = PinHasher.PIN_LENGTH,
    isError: Boolean = false
) {
    val active = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(length) { index ->
            val color by animateColorAsState(if (index < filled) active else empty, label = "pinDot")
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(16.dp)
            ) {}
        }
    }
}

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) { content() }
    }
}

/**
 * Digits 1-9, then an optional slot (the fingerprint button), 0 and backspace.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startSlot: (@Composable () -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    PinKey(enabled = enabled, onClick = { tap { onDigit(digit) } }) {
                        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) { startSlot?.invoke() }
            PinKey(enabled = enabled, onClick = { tap { onDigit(0) } }) {
                Text("0", style = MaterialTheme.typography.headlineSmall)
            }
            PinKey(enabled = enabled, onClick = { tap(onBackspace) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.pin_backspace)
                )
            }
        }
    }
}

/**
 * Dots and keypad together. Calls [onComplete] with the PIN once all digits are in.
 * Change [resetKey] to clear what has been typed, for example after a wrong PIN.
 */
@Composable
fun PinEntry(
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    startSlot: (@Composable () -> Unit)? = null
) {
    var pin by remember(resetKey) { mutableStateOf("") }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        PinDots(filled = pin.length, isError = isError)
        PinKeypad(
            enabled = enabled,
            startSlot = startSlot,
            onDigit = { digit ->
                if (pin.length < PinHasher.PIN_LENGTH) {
                    pin += digit
                    if (pin.length == PinHasher.PIN_LENGTH) onComplete(pin)
                }
            },
            onBackspace = { pin = pin.dropLast(1) }
        )
    }
}
