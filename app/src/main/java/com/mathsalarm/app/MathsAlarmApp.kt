package com.mathsalarm.app

import android.app.Application
import com.mathsalarm.app.alarm.AlarmService
import com.mathsalarm.app.data.AlarmStore

class MathsAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmStore.init(this)
        AlarmService.createChannel(this)
    }
}
