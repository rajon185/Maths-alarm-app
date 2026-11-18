package com.mathsalarm.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mathsalarm.app.data.AlarmStore
import com.mathsalarm.app.model.Alarm
import com.mathsalarm.app.ui.MainActivity
import java.time.ZonedDateTime

object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun requestCode(alarmId: Int, snooze: Boolean) = alarmId * 2 + if (snooze) 1 else 0

    private fun firePendingIntent(context: Context, alarmId: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("com.mathsalarm.app.FIRE.$alarmId.$snooze")
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_IS_SNOOZE, snooze)
        return PendingIntent.getBroadcast(
            context, requestCode(alarmId, snooze), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context).canScheduleExactAlarms()

    private fun setAt(context: Context, triggerAtMillis: Long, operation: PendingIntent) {
        val am = alarmManager(context)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent(context)), operation)
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked: fall back to a best-effort alarm.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    /** Schedules the next regular occurrence (or cancels it if the alarm is off). Returns trigger time. */
    fun schedule(context: Context, alarm: Alarm): ZonedDateTime? {
        val pi = firePendingIntent(context, alarm.id, snooze = false)
        alarmManager(context).cancel(pi)
        if (!alarm.enabled) return null
        val trigger = nextTrigger(alarm, ZonedDateTime.now())
        setAt(context, trigger.toInstant().toEpochMilli(), pi)
        return trigger
    }

    fun scheduleSnooze(context: Context, alarmId: Int, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        setAt(context, at, firePendingIntent(context, alarmId, snooze = true))
    }

    fun cancelSnooze(context: Context, alarmId: Int) {
        alarmManager(context).cancel(firePendingIntent(context, alarmId, snooze = true))
    }

    fun cancel(context: Context, alarmId: Int) {
        alarmManager(context).cancel(firePendingIntent(context, alarmId, snooze = false))
        cancelSnooze(context, alarmId)
    }

    fun rescheduleAll(context: Context) {
        AlarmStore.init(context)
        AlarmStore.all().forEach { schedule(context, it) }
    }
}
