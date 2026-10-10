package com.alam.todoalarm

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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 알람이 울리는 동안 소리·진동을 내고, 잠금화면 위에 할일 화면을 띄우는 서비스. */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { finishRinging() }
    private var currentId = PREVIEW_ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentId = intent.getIntExtra(EXTRA_ID, PREVIEW_ID)
                val todo = intent.getStringExtra(EXTRA_TODO).orEmpty()
                startRinging(todo)
            }
            ACTION_DONE -> finishRinging()
            ACTION_SNOOZE -> {
                if (currentId != PREVIEW_ID) {
                    AlarmStore.get(this, currentId)?.let {
                        it.snoozeUntil = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
                        AlarmStore.upsert(this, it)
                        AlarmScheduler.schedule(this, it)
                    }
                }
                finishRinging()
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(todo: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // 소리는 서비스가 직접 재생
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )

        val screen = Intent(this, AlarmActivity::class.java).apply {
            putExtra(EXTRA_TODO, todo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        val screenPi = PendingIntent.getActivity(
            this, 0, screen, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = todo.ifBlank { "알람" }
        val notif = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("⏰ $title")
            .setContentText("지금 할 일")
            .setStyle(Notification.BigTextStyle().bigText(title))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screenPi)
            .setFullScreenIntent(screenPi, true)
            .addAction(Notification.Action.Builder(null, "✔ 완료", actionPi(ACTION_DONE, 1)).build())
            .addAction(Notification.Action.Builder(null, "5분 뒤", actionPi(ACTION_SNOOZE, 2)).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        // 화면이 켜져 있어 전체화면 알림이 배너로만 뜨는 경우를 위해 직접 띄우기도 시도
        try { startActivity(screen) } catch (_: Exception) {}

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "todoalarm:ring")
                .apply { acquire(AUTO_STOP_MS + 5_000L) }
        }
        playSound()
        vibrate()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun actionPi(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, AlarmService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun playSound() {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator?.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        } else {
            vibrator?.vibrate(effect)
        }
    }

    private fun stopSound() {
        player?.run { try { stop() } catch (_: Exception) {}; release() }
        player = null
    }

    private fun finishRinging() {
        handler.removeCallbacks(autoStop)
        stopSound()
        vibrator?.cancel()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopSound()
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.alam.todoalarm.START"
        const val ACTION_DONE = "com.alam.todoalarm.DONE"
        const val ACTION_SNOOZE = "com.alam.todoalarm.SNOOZE"
        const val ACTION_STOPPED = "com.alam.todoalarm.STOPPED"
        const val EXTRA_ID = "id"
        const val EXTRA_TODO = "todo"
        const val PREVIEW_ID = -1
        const val SNOOZE_MIN = 5
        private const val CHANNEL = "alarm_ring"
        private const val NOTIF_ID = 1001
        private const val AUTO_STOP_MS = 10 * 60_000L

        fun start(ctx: Context, id: Int, todo: String) {
            val i = Intent(ctx, AlarmService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TODO, todo)
            }
            ctx.startForegroundService(i)
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, AlarmService::class.java).setAction(action))
        }
    }
}
