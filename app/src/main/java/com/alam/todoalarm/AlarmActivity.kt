package com.alam.todoalarm

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** 알람이 울릴 때 잠금화면 위에 뜨는 전체화면. 할일을 화면 가득 크게 보여줌. */
class AlarmActivity : Activity() {

    private val animators = mutableListOf<ValueAnimator>()

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        showTodo(intent)

        findViewById<View>(R.id.doneBtn).setOnClickListener {
            AlarmService.send(this, AlarmService.ACTION_DONE)
            finish()
        }
        findViewById<View>(R.id.snoozeBtn).setOnClickListener {
            AlarmService.send(this, AlarmService.ACTION_SNOOZE)
            finish()
        }

        // 검정 ↔ 빨강 깜빡이는 배경
        animators += ObjectAnimator.ofObject(
            findViewById<View>(R.id.root), "backgroundColor", ArgbEvaluator(),
            Color.BLACK, Color.parseColor("#3D0000")
        ).apply { duration = 500; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start() }

        // 할일 글자 살짝 커졌다 작아졌다
        val todo = findViewById<View>(R.id.ringTodo)
        animators += ValueAnimator.ofFloat(1f, 1.04f).apply {
            duration = 500; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { todo.scaleX = it.animatedValue as Float; todo.scaleY = it.animatedValue as Float }
            start()
        }

        val filter = IntentFilter(AlarmService.ACTION_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stoppedReceiver, filter)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showTodo(intent)
    }

    private fun showTodo(i: Intent) {
        val todo = i.getStringExtra(AlarmService.EXTRA_TODO).orEmpty().ifBlank { "알람" }
        findViewById<TextView>(R.id.ringTodo).text = todo
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 뒤로가기로 실수로 끄지 않도록 무시 — 버튼으로만 끔
    }

    override fun onDestroy() {
        animators.forEach { it.cancel() }
        unregisterReceiver(stoppedReceiver)
        super.onDestroy()
    }
}
