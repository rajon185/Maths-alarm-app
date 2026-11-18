package com.mathsalarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers alarms after reboot, app update, clock/timezone changes or permission grants. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
    }
}
