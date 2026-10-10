package com.alam.todoalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)
        val alarm = AlarmStore.get(ctx, id) ?: return

        if (snooze) {
            if (alarm.snoozeUntil == 0L) return
            alarm.snoozeUntil = 0L
        } else {
            if (!alarm.on) return
            // 1회 알람은 울린 뒤 꺼짐, 반복 알람은 다음 요일로 다시 예약
            if (alarm.days.isEmpty()) alarm.on = false
        }
        AlarmStore.upsert(ctx, alarm)
        AlarmScheduler.schedule(ctx, alarm)

        AlarmService.start(ctx, alarm.id, alarm.todo)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(ctx)
    }
}
