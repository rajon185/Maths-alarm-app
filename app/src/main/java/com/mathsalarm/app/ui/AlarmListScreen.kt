package com.mathsalarm.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathsalarm.app.model.Alarm
import com.mathsalarm.app.model.daysSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    alarms: List<Alarm>,
    issues: List<SetupIssue>,
    onAdd: () -> Unit,
    onEdit: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Maths Alarm") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New alarm") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(issues, key = { "issue_" + it.title }) { IssueCard(it) }
            if (alarms.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No alarms yet.\nTap “New alarm” to add one.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm,
                    onClick = { onEdit(alarm) },
                    onDuplicate = { onDuplicate(alarm) },
                    onToggle = { onToggle(alarm, it) },
                )
            }
        }
    }
}

@Composable
private fun IssueCard(issue: SetupIssue) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(issue.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(issue.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = issue.fix) { Text("Fix") }
        }
    }
}

/**
 * A large clock-style time. In 12-hour mode the AM/PM is drawn smaller beside the digits, and the
 * text never wraps, so big sizes can't spill onto a second (overlapping) line.
 */
@Composable
fun TimeText(
    hour: Int,
    minute: Int,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Light,
) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val text = buildAnnotatedString {
        if (is24) {
            append("%02d:%02d".format(hour, minute))
        } else {
            append("%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute))
            withStyle(SpanStyle(fontSize = fontSize * 0.4f, fontWeight = FontWeight.Normal)) {
                append(if (hour < 12) " AM" else " PM")
            }
        }
    }
    Text(
        text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = fontSize * 1.15f,
        maxLines = 1,
        softWrap = false,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onDuplicate: () -> Unit, onToggle: (Boolean) -> Unit) {
    val dim = if (alarm.enabled) 1f else 0.5f
    var menuOpen by remember { mutableStateOf(false) }
    // Long-press opens a small menu; the Box anchors it to the card.
    Box {
        Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TimeText(
                        alarm.hour,
                        alarm.minute,
                        fontSize = 40.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                    )
                    val title = listOfNotNull(alarm.label.ifBlank { null }, alarm.days.daysSummary()).joinToString(" · ")
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
                    Spacer(Modifier.height(4.dp))
                    val types = alarm.problemTypes.sortedBy { it.ordinal }.joinToString(", ") { it.label }
                    val snooze = when {
                        !alarm.snoozeEnabled -> "no snooze"
                        alarm.maxSnoozes == 0 -> "snooze ${alarm.snoozeMinutes} min"
                        else -> "snooze ${alarm.snoozeMinutes} min ×${alarm.maxSnoozes}"
                    }
                    Text(
                        "${alarm.problemCount} × $types · ${alarm.difficulty.label} · $snooze",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                    )
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onClick() })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuOpen = false; onDuplicate() })
        }
    }
}
