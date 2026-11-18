package com.mathsalarm.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.mathsalarm.app.model.Alarm
import com.mathsalarm.app.model.Difficulty
import com.mathsalarm.app.model.ProblemType
import com.mathsalarm.app.model.shortName
import java.time.DayOfWeek
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    initial: Alarm,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (alarm: Alarm, test: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var alarm by remember(initial) { mutableStateOf(initial) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            alarm = alarm.copy(ringtoneUri = uri?.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New alarm" else "Edit alarm") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
                actions = {
                    if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- time
            Text(
                formatTime(alarm.hour, alarm.minute),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { showTimePicker = true }
                    .padding(8.dp),
            )

            OutlinedTextField(
                value = alarm.label,
                onValueChange = { alarm = alarm.copy(label = it.take(40)) },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- days
            Section("Repeat") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day.value in alarm.days
                        FilterChip(
                            selected = selected,
                            onClick = {
                                alarm = alarm.copy(days = if (selected) alarm.days - day.value else alarm.days + day.value)
                            },
                            label = { Text(day.shortName()) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Once" to emptySet(),
                        "Weekdays" to setOf(1, 2, 3, 4, 5),
                        "Weekends" to setOf(6, 7),
                        "Every day" to (1..7).toSet(),
                    ).forEach { (name, set) ->
                        FilterChip(selected = alarm.days == set, onClick = { alarm = alarm.copy(days = set) }, label = { Text(name) })
                    }
                }
                Hint(if (alarm.days.isEmpty()) "Rings once, then turns itself off." else "Rings on the selected days every week.")
            }

            // ---- problems
            Section("Problems to solve") {
                Text("Types (a random one is picked for each problem)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProblemType.entries.forEach { type ->
                        val selected = type in alarm.problemTypes
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) alarm.problemTypes - type else alarm.problemTypes + type
                                if (next.isNotEmpty()) alarm = alarm.copy(problemTypes = next)
                            },
                            label = { Text(type.label) },
                        )
                    }
                }
                Hint(alarm.problemTypes.sortedBy { it.ordinal }.joinToString(" · ") { "${it.label}: ${it.description}" })

                Text("Difficulty", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Difficulty.entries.forEach { d ->
                        FilterChip(selected = alarm.difficulty == d, onClick = { alarm = alarm.copy(difficulty = d) }, label = { Text(d.label) })
                    }
                }

                LabeledSlider(
                    label = "Number of problems",
                    valueText = "${alarm.problemCount}",
                    value = alarm.problemCount,
                    range = 1..20,
                    onChange = { alarm = alarm.copy(problemCount = it) },
                )
                LabeledSlider(
                    label = "Silence while solving",
                    valueText = "${alarm.silenceSeconds} s",
                    value = alarm.silenceSeconds,
                    range = 10..120,
                    step = 5,
                    onChange = { alarm = alarm.copy(silenceSeconds = it) },
                )
                Hint("Tapping Dismiss mutes the alarm for this long. Every correct answer restarts the timer; if it runs out the alarm rings again until you solve them all.")
            }

            // ---- snooze
            Section("Snooze") {
                SwitchRow("Allow snooze", alarm.snoozeEnabled) { alarm = alarm.copy(snoozeEnabled = it) }
                if (alarm.snoozeEnabled) {
                    LabeledSlider(
                        label = "Snooze length",
                        valueText = "${alarm.snoozeMinutes} min",
                        value = alarm.snoozeMinutes,
                        range = 1..30,
                        onChange = { alarm = alarm.copy(snoozeMinutes = it) },
                    )
                    LabeledSlider(
                        label = "Maximum snoozes",
                        valueText = if (alarm.maxSnoozes == 0) "Unlimited" else "${alarm.maxSnoozes}",
                        value = alarm.maxSnoozes,
                        range = 0..10,
                        onChange = { alarm = alarm.copy(maxSnoozes = it) },
                    )
                    Hint("Once the snoozes are used up you must solve the problems to turn the alarm off.")
                } else {
                    Hint("No snoozing: the only way to stop this alarm is to solve the problems.")
                }
            }

            // ---- sound
            Section("Sound") {
                val ringtoneTitle = remember(alarm.ringtoneUri) {
                    alarm.ringtoneUri?.let { uri ->
                        try { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) } catch (_: Exception) { null }
                    } ?: "Default alarm sound"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    alarm.ringtoneUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                                )
                            try { ringtoneLauncher.launch(intent) } catch (_: Exception) {}
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Alarm sound", Modifier.weight(1f))
                    Text(ringtoneTitle, color = MaterialTheme.colorScheme.primary)
                }
                SwitchRow("Vibrate", alarm.vibrate) { alarm = alarm.copy(vibrate = it) }
            }

            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { onSave(alarm, true) }, modifier = Modifier.weight(1f)) { Text("Save & test") }
                Button(onClick = { onSave(alarm, false) }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(alarm.hour, alarm.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    alarm = alarm.copy(hour = state.hour, minute = state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this alarm?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text(valueText, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        val stepsCount = (range.last - range.first) / step - 1
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val snapped = range.first + ((v - range.first) / step).roundToInt() * step
                onChange(snapped.coerceIn(range.first, range.last))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = stepsCount.coerceAtLeast(0),
        )
        Spacer(Modifier.height(2.dp))
    }
}
