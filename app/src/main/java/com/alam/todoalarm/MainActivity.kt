package com.alam.todoalarm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.addBtn).setOnClickListener {
            startActivity(Intent(this, EditActivity::class.java))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        AlarmScheduler.scheduleAll(this)
        render()
        checkPermissions()
    }

    private fun render() {
        val list = findViewById<LinearLayout>(R.id.list)
        list.removeAllViews()
        val alarms = AlarmStore.all(this).sortedWith(compareBy({ it.hour }, { it.minute }))
        findViewById<View>(R.id.empty).visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        val fmt = SimpleDateFormat("HH:mm", Locale.KOREA)

        for (a in alarms) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_alarm, list, false)
            row.alpha = if (a.on) 1f else 0.45f
            row.findViewById<TextView>(R.id.time).text = a.timeText
            row.findViewById<TextView>(R.id.todo).text = a.todo.ifBlank { "(할일 없음)" }
            var sub = a.daysText
            if (a.snoozeUntil > System.currentTimeMillis()) sub += " · ${fmt.format(Date(a.snoozeUntil))} 다시 울림"
            row.findViewById<TextView>(R.id.days).text = sub
            row.findViewById<Switch>(R.id.toggle).apply {
                isChecked = a.on
                setOnCheckedChangeListener { _, checked ->
                    a.on = checked
                    a.snoozeUntil = 0L
                    AlarmStore.upsert(this@MainActivity, a)
                    AlarmScheduler.schedule(this@MainActivity, a)
                    render()
                }
            }
            row.setOnClickListener {
                startActivity(Intent(this, EditActivity::class.java).putExtra(EditActivity.EXTRA_ID, a.id))
            }
            list.addView(row)
        }
    }

    /** 알람이 잠금화면에서도 크게 뜨려면 필요한 권한 안내. */
    private fun checkPermissions() {
        val warn = findViewById<TextView>(R.id.warn)
        val nm = getSystemService(NotificationManager::class.java)
        val (msg, action) = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                "⚠ 알림 권한이 꺼져 있어요. 눌러서 켜주세요." to Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

            !AlarmScheduler.canScheduleExact(this) ->
                "⚠ '알람 및 리마인더' 권한이 필요해요. 눌러서 허용해주세요." to
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))

            Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent() ->
                "⚠ 잠금화면에 할일을 크게 띄우려면 '전체 화면 알림' 권한을 허용해주세요." to
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))

            else -> null to null
        }
        if (msg == null) {
            warn.visibility = View.GONE
        } else {
            warn.visibility = View.VISIBLE
            warn.text = msg
            warn.setOnClickListener {
                try { startActivity(action) } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
        }
    }
}
