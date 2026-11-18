package com.mathsalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mathsalarm.app.data.AlarmStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)
        AlarmStore.init(context)
        val alarm = AlarmStore.get(id) ?: return

        if (!isSnooze) {
            if (!alarm.enabled) return
            if (alarm.isRepeating) {
                AlarmScheduler.schedule(context, alarm)
            } else {
                AlarmStore.save(alarm.copy(enabled = false))
            }
            // A fresh ring starts a fresh snooze budget.
            AlarmStore.setSnoozeCount(id, 0)
            AlarmScheduler.cancelSnooze(context, id)
        }

        val current = RingingState.ringingAlarmId.value
        if (current != null && current != id) {
            // Another alarm is already ringing: try this one again in a minute.
            AlarmScheduler.scheduleSnooze(context, id, 1)
            return
        }
        AlarmService.start(context, id)
    }
}
