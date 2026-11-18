package com.mathsalarm.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mathsalarm.app.alarm.AlarmScheduler
import com.mathsalarm.app.alarm.AlarmService
import com.mathsalarm.app.alarm.RingingState
import com.mathsalarm.app.data.AlarmStore
import com.mathsalarm.app.model.Alarm
import com.mathsalarm.app.model.ProblemType
import com.mathsalarm.app.problems.Problem
import com.mathsalarm.app.problems.ProblemGenerator
import com.mathsalarm.app.ui.theme.MathsAlarmTheme
import kotlinx.coroutines.delay
import java.time.LocalTime

class RingingActivity : ComponentActivity() {

    companion object {
        fun intent(context: Context, alarmId: Int): Intent =
            Intent(context, RingingActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        AlarmStore.init(this)

        val alarmId = RingingState.ringingAlarmId.value
        val alarm = alarmId?.let { AlarmStore.get(it) }
        if (alarm == null) {
            finish()
            return
        }

        setContent {
            MathsAlarmTheme(forceDark = true) {
                val ringing by RingingState.ringingAlarmId.collectAsState()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                BackHandler { /* The alarm can't be escaped with the back button. */ }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RingingScreen(
                        alarm = alarm,
                        onSnooze = { snooze(alarm) },
                        onSolved = { dismiss(alarm) },
                        onSilence = { AlarmService.silence(this, alarm.silenceSeconds * 1000L) },
                    )
                }
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun snooze(alarm: Alarm) {
        AlarmStore.setSnoozeCount(alarm.id, AlarmStore.snoozeCount(alarm.id) + 1)
        AlarmScheduler.scheduleSnooze(this, alarm.id, alarm.snoozeMinutes)
        AlarmService.stop(this)
        finish()
    }

    private fun dismiss(alarm: Alarm) {
        AlarmStore.setSnoozeCount(alarm.id, 0)
        AlarmScheduler.cancelSnooze(this, alarm.id)
        AlarmService.stop(this)
        finish()
    }
}

@Composable
private fun RingingScreen(
    alarm: Alarm,
    onSnooze: () -> Unit,
    onSolved: () -> Unit,
    onSilence: () -> Unit,
) {
    var solving by remember { mutableStateOf(false) }
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf(ProblemGenerator.generate(alarm.problemTypes, alarm.difficulty)) }
    var feedback by remember { mutableStateOf<String?>(null) }
    // Bumped on every submission so the answer input always starts fresh, even if a problem repeats.
    var round by remember { mutableIntStateOf(0) }

    val snoozesUsed = remember { AlarmStore.snoozeCount(alarm.id) }
    val snoozesLeft = if (alarm.maxSnoozes == 0) Int.MAX_VALUE else alarm.maxSnoozes - snoozesUsed
    val canSnooze = alarm.snoozeEnabled && snoozesLeft > 0

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Clock(big = !solving)
        Text(
            alarm.label.ifBlank { "Alarm" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!solving) {
            Spacer(Modifier.weight(1f))
            Text(
                "Solve ${alarm.problemCount} problem${if (alarm.problemCount == 1) "" else "s"} to turn this alarm off",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSilence(); solving = true },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("Dismiss — start solving", fontSize = 18.sp) }
            Spacer(Modifier.height(12.dp))
            SnoozeButton(alarm, canSnooze, snoozesLeft, onSnooze)
            Spacer(Modifier.height(24.dp))
        } else {
            Spacer(Modifier.height(8.dp))
            SilenceBanner(onSilence)
            Spacer(Modifier.height(8.dp))
            Text("Problem ${solved + 1} of ${alarm.problemCount}", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(
                progress = { solved / alarm.problemCount.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            feedback?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(round) { ProblemView(
                    problem = problem,
                    onSubmit = { input ->
                        round += 1
                        if (problem.isCorrect(input)) {
                            feedback = null
                            solved += 1
                            if (solved >= alarm.problemCount) {
                                onSolved()
                            } else {
                                problem = ProblemGenerator.generate(alarm.problemTypes, alarm.difficulty)
                                onSilence() // reward progress with a fresh quiet period
                            }
                            true
                        } else {
                            if (problem.type == ProblemType.TYPING) {
                                feedback = "Not quite — check it carefully and try again."
                            } else {
                                feedback = "Wrong — here's a new one."
                                problem = ProblemGenerator.generate(alarm.problemTypes, alarm.difficulty)
                            }
                            false
                        }
                    },
                ) }
            }
            if (canSnooze) SnoozeButton(alarm, true, snoozesLeft, onSnooze)
        }
    }
}

@Composable
private fun Clock(big: Boolean) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { now = LocalTime.now(); delay(1000) }
    }
    val size by animateFloatAsState(if (big) 84f else 40f, tween(300), label = "clock")
    Text(
        formatTime(now.hour, now.minute),
        fontSize = size.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(top = if (big) 48.dp else 4.dp),
    )
}

@Composable
private fun SnoozeButton(alarm: Alarm, canSnooze: Boolean, snoozesLeft: Int, onSnooze: () -> Unit) {
    when {
        canSnooze -> OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            val left = if (snoozesLeft == Int.MAX_VALUE) "" else " ($snoozesLeft left)"
            Text("Snooze ${alarm.snoozeMinutes} min$left")
        }
        alarm.snoozeEnabled -> Text("No snoozes left — solve to dismiss", color = MaterialTheme.colorScheme.error)
        else -> Text("Snooze is disabled for this alarm", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SilenceBanner(onSilence: () -> Unit) {
    val until by RingingState.silencedUntil.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (true) { now = System.currentTimeMillis(); delay(250) }
    }
    val remaining = ((until - now + 999) / 1000).coerceAtLeast(0)
    if (until > 0 && remaining > 0) {
        Text(
            "🔇 Silenced — rings again in ${remaining}s",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
        )
    } else {
        FilledTonalButton(
            onClick = onSilence,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) { Text("🔔 Ringing — tap to silence") }
    }
}

/** Shows a problem and collects an answer. [onSubmit] returns true if the answer was right. */
@Composable
private fun ProblemView(problem: Problem, onSubmit: (String) -> Boolean) {
    // Keyed on the problem instance so the input resets for every new (or retried) problem.
    var input by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(problem.memorizeMillis == 0L) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text(problem.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (problem.type == ProblemType.MEMORY) {
            MemoryPrompt(problem, revealed = !hidden, onHide = { hidden = true })
            if (!hidden) return@Column
        } else {
            Text(
                problem.prompt,
                fontSize = if (problem.prompt.length > 22) 26.sp else 34.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (problem.type == ProblemType.TYPING) FontFamily.Monospace else FontFamily.Default,
                textAlign = TextAlign.Center,
            )
        }

        if (problem.numeric) {
            Text(
                input.ifEmpty { "?" },
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (input.isEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            )
            NumberPad(
                onDigit = { if (input.length < 12) input += it },
                onBackspace = { input = input.dropLast(1) },
                onSubmit = { if (input.isNotEmpty()) onSubmit(input) },
            )
        } else {
            val focus = remember { FocusRequester() }
            LaunchedEffect(problem) { try { focus.requestFocus() } catch (_: Exception) {} }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.replace("\n", "") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onSubmit(input) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Button(onClick = { if (input.isNotBlank()) onSubmit(input) }, modifier = Modifier.fillMaxWidth()) { Text("Submit") }
        }
    }
}

@Composable
private fun MemoryPrompt(problem: Problem, revealed: Boolean, onHide: () -> Unit) {
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(problem) {
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            progress = 1f - elapsed / problem.memorizeMillis.toFloat()
            if (elapsed >= problem.memorizeMillis) break
            delay(50)
        }
        onHide()
    }
    if (revealed) {
        Text(
            problem.prompt.chunked(3).joinToString(" "),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            fontFamily = FontFamily.Monospace,
        )
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(0.6f))
    } else {
        Text("Now enter the number you saw", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NumberPad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val modifier = Modifier.weight(1f).aspectRatio(2f)
                    when (key) {
                        "✓" -> Button(onClick = onSubmit, modifier = modifier) { Text("✓", fontSize = 24.sp) }
                        "⌫" -> FilledTonalButton(onClick = onBackspace, modifier = modifier) { Text("⌫", fontSize = 22.sp) }
                        else -> FilledTonalButton(onClick = { onDigit(key) }, modifier = modifier) { Text(key, fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}
