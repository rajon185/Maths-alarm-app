package com.mathsalarm.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mathsalarm.app.R
import com.mathsalarm.app.data.AlarmStore
import com.mathsalarm.app.model.Alarm
import com.mathsalarm.app.ui.RingingActivity

/** Foreground service that plays the alarm sound and vibration until the alarm is snoozed or solved. */
class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIFICATION_ID = 4242
        private const val ACTION_START = "com.mathsalarm.app.START"
        private const val ACTION_SILENCE = "com.mathsalarm.app.SILENCE"
        private const val ACTION_STOP = "com.mathsalarm.app.STOP"
        private const val EXTRA_SILENCE_MS = "silence_ms"
        private const val EXTRA_TEST = "test"

        fun start(context: Context, alarmId: Int, test: Boolean = false) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_TEST, test)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Temporarily pause the sound while the user is solving. */
        fun silence(context: Context, millis: Long) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(ACTION_SILENCE).putExtra(EXTRA_SILENCE_MS, millis),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an alarm is ringing"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarm: Alarm? = null
    private val resumeRunnable = Runnable { resumeRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AlarmStore.init(this)
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
                val alarm = AlarmStore.get(id)
                if (alarm == null) {
                    // Must still satisfy the startForegroundService contract before stopping.
                    startInForeground(buildNotification(null))
                    stopEverything()
                    return START_NOT_STICKY
                }
                startRinging(alarm, intent.getBooleanExtra(EXTRA_TEST, false))
            }
            ACTION_SILENCE -> {
                if (currentAlarm != null) silenceFor(intent.getLongExtra(EXTRA_SILENCE_MS, 30_000L))
            }
            ACTION_STOP -> stopEverything()
            else -> if (currentAlarm == null) stopSelf()
        }
        return START_REDELIVER_INTENT
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun startRinging(alarm: Alarm, test: Boolean) {
        stopSound()
        handler.removeCallbacks(resumeRunnable)
        currentAlarm = alarm
        RingingState.setSilencedUntil(0L)
        RingingState.setRinging(alarm.id, test)
        startInForeground(buildNotification(alarm))
        acquireWakeLock()
        playSound(alarm)
        if (alarm.vibrate) startVibration()
        // Try to bring up the screen directly; when not allowed the full-screen notification does it.
        try {
            startActivity(RingingActivity.intent(this, alarm.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun silenceFor(millis: Long) {
        handler.removeCallbacks(resumeRunnable)
        player?.let { if (it.isPlaying) it.pause() }
        vibrator?.cancel()
        RingingState.setSilencedUntil(System.currentTimeMillis() + millis)
        handler.postDelayed(resumeRunnable, millis)
    }

    private fun resumeRinging() {
        val alarm = currentAlarm ?: return
        RingingState.setSilencedUntil(0L)
        val p = player
        if (p != null) {
            try { p.start() } catch (_: Exception) { playSound(alarm) }
        } else {
            playSound(alarm)
        }
        if (alarm.vibrate) startVibration()
    }

    private fun playSound(alarm: Alarm) {
        val candidates = listOfNotNull(
            alarm.ringtoneUri?.let { Uri.parse(it) },
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (_: Exception) {
                mp.release()
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        @Suppress("DEPRECATION")
        v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    private fun stopSound() {
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
        vibrator?.cancel()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MathsAlarm:ringing")
            .apply { acquire(60 * 60 * 1000L) }
    }

    private fun stopEverything() {
        handler.removeCallbacks(resumeRunnable)
        stopSound()
        currentAlarm = null
        RingingState.setSilencedUntil(0L)
        RingingState.setRinging(null)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(resumeRunnable)
        stopSound()
        RingingState.setRinging(null)
        RingingState.setSilencedUntil(0L)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun buildNotification(alarm: Alarm?): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, alarm?.id ?: 0, RingingActivity.intent(this, alarm?.id ?: -1),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = alarm?.label?.ifBlank { null } ?: "Alarm"
        val text = alarm?.let { "Solve ${it.problemCount} problem${if (it.problemCount == 1) "" else "s"} to dismiss" } ?: ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }
}
