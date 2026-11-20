package com.mathsalarm.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mathsalarm.app.alarm.AlarmScheduler
import com.mathsalarm.app.alarm.AlarmService
import com.mathsalarm.app.alarm.timeUntilText
import com.mathsalarm.app.data.AlarmStore
import com.mathsalarm.app.model.Alarm
import com.mathsalarm.app.ui.theme.MathsAlarmTheme
import java.time.ZonedDateTime

/** A permission or setting the app needs for alarms to be reliable. */
data class SetupIssue(val title: String, val detail: String, val fix: () -> Unit)

class MainActivity : ComponentActivity() {

    private val issues = mutableStateOf<List<SetupIssue>>(emptyList())
    private lateinit var requestNotifications: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlarmStore.init(this)
        AlarmScheduler.rescheduleAll(this)

        requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshIssues() }
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshIssues()
        })

        setContent {
            MathsAlarmTheme {
                val alarms by AlarmStore.alarms.collectAsState()
                var editing by remember { mutableStateOf<Alarm?>(null) }
                var duplicating by remember { mutableStateOf(false) }
                fun openEditor(alarm: Alarm?, duplicate: Boolean = false) {
                    editing = alarm
                    duplicating = duplicate
                }
                // A duplicate is a brand-new alarm (id -1) carrying all the source alarm's settings.
                fun duplicate(source: Alarm) = openEditor(source.copy(id = -1, enabled = true), duplicate = true)
                val currentIssues by issues

                val editor = editing
                if (editor == null) {
                    AlarmListScreen(
                        alarms = alarms,
                        issues = currentIssues,
                        onAdd = { openEditor(Alarm(id = -1)) },
                        onEdit = { openEditor(it) },
                        onDuplicate = { duplicate(it) },
                        onToggle = { alarm, on -> setEnabled(alarm, on) },
                    )
                } else {
                    BackHandler { openEditor(null) }
                    AlarmEditScreen(
                        initial = editor,
                        isNew = editor.id < 0,
                        isDuplicate = duplicating,
                        onCancel = { openEditor(null) },
                        onDuplicate = { duplicate(it) },
                        onSave = { saved, test ->
                            val stored = saveAlarm(saved)
                            openEditor(null)
                            if (test) AlarmService.start(this, stored.id, test = true)
                        },
                        onDelete = {
                            if (editor.id >= 0) {
                                AlarmScheduler.cancel(this, editor.id)
                                AlarmStore.delete(editor.id)
                            }
                            openEditor(null)
                        },
                    )
                }
            }
        }
    }

    private fun saveAlarm(alarm: Alarm): Alarm {
        val toStore = if (alarm.id < 0) alarm.copy(id = AlarmStore.newId(), enabled = true) else alarm.copy(enabled = true)
        AlarmStore.save(toStore)
        AlarmScheduler.cancelSnooze(this, toStore.id)
        val trigger = AlarmScheduler.schedule(this, toStore)
        if (trigger != null) toast(timeUntilText(ZonedDateTime.now(), trigger))
        return toStore
    }

    private fun setEnabled(alarm: Alarm, on: Boolean) {
        val updated = alarm.copy(enabled = on)
        AlarmStore.save(updated)
        if (on) {
            AlarmScheduler.schedule(this, updated)?.let { toast(timeUntilText(ZonedDateTime.now(), it)) }
        } else {
            AlarmScheduler.cancel(this, alarm.id)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun refreshIssues() {
        val list = mutableListOf<SetupIssue>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            list += SetupIssue("Allow notifications", "Needed to show the alarm screen when it rings.") {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += SetupIssue("Allow alarms & reminders", "Needed so alarms ring at the exact time.") {
                openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        ) {
            list += SetupIssue("Allow full-screen alarms", "Lets the alarm take over the lock screen.") {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            list += SetupIssue("Disable battery optimisation", "Recommended: stops the system delaying or killing alarms.") {
                @Suppress("BatteryLife")
                openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        issues.value = list
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}

