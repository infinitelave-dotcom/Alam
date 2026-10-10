package com.alam.todoalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_FIRE = "com.alam.todoalarm.FIRE"
    const val EXTRA_ID = "id"
    const val EXTRA_SNOOZE = "snooze"

    /** 지금 이후 이 알람이 울릴 다음 시각 (ms). */
    fun nextTrigger(a: Alarm, now: Long = System.currentTimeMillis()): Long {
        val base = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, a.hour)
            set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            val c = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
            val dow = c.get(Calendar.DAY_OF_WEEK) - 1
            if (c.timeInMillis > now && (a.days.isEmpty() || dow in a.days)) return c.timeInMillis
        }
        return base.timeInMillis + 24 * 60 * 60 * 1000L
    }

    private fun firePending(ctx: Context, id: Int, snooze: Boolean): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_SNOOZE, snooze)
        }
        return PendingIntent.getBroadcast(
            ctx, id * 2 + if (snooze) 1 else 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showPending(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    fun canScheduleExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    private fun setAt(ctx: Context, time: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(ctx)) {
            // 시스템 알람시계와 같은 방식: 잠금/절전 상태에서도 정확히 울림
            am.setAlarmClock(AlarmManager.AlarmClockInfo(time, showPending(ctx)), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun schedule(ctx: Context, a: Alarm) {
        cancel(ctx, a.id)
        if (a.on) setAt(ctx, nextTrigger(a), firePending(ctx, a.id, false))
        // 1회 알람이 꺼진 뒤에도 '5분 뒤 다시'는 울려야 함
        if (a.snoozeUntil > System.currentTimeMillis()) {
            setAt(ctx, a.snoozeUntil, firePending(ctx, a.id, true))
        }
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(firePending(ctx, id, false))
        am.cancel(firePending(ctx, id, true))
    }

    fun scheduleAll(ctx: Context) = AlarmStore.all(ctx).forEach { schedule(ctx, it) }
}
