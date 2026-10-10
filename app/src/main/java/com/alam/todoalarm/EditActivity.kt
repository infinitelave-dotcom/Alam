package com.alam.todoalarm

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import java.util.Calendar

class EditActivity : Activity() {

    private var editing: Alarm? = null
    private val selDays = mutableSetOf<Int>()
    private val dayButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        val id = intent.getIntExtra(EXTRA_ID, -1)
        editing = if (id >= 0) AlarmStore.get(this, id) else null

        val picker = findViewById<TimePicker>(R.id.timePicker).apply { setIs24HourView(true) }
        val todoInput = findViewById<EditText>(R.id.todoInput)
        findViewById<TextView>(R.id.title).text = if (editing != null) "알람 수정" else "알람 추가"

        editing?.let {
            picker.hour = it.hour
            picker.minute = it.minute
            todoInput.setText(it.todo)
            selDays += it.days
        } ?: run {
            val soon = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
            picker.hour = soon.get(Calendar.HOUR_OF_DAY)
            picker.minute = soon.get(Calendar.MINUTE)
        }

        val dayRow = findViewById<LinearLayout>(R.id.days)
        DAY_NAMES.forEachIndexed { i, name ->
            val b = Button(this).apply {
                text = name
                textSize = 16f
                setBackgroundResource(R.drawable.rounded)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    if (i > 0) marginStart = (6 * resources.displayMetrics.density).toInt()
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    if (!selDays.remove(i)) selDays.add(i)
                    paintDays()
                }
            }
            dayButtons += b
            dayRow.addView(b)
        }
        paintDays()

        findViewById<View>(R.id.cancelBtn).setOnClickListener { finish() }

        findViewById<View>(R.id.saveBtn).setOnClickListener {
            val a = (editing ?: Alarm(AlarmStore.nextId(this), 0, 0, "", emptySet())).apply {
                hour = picker.hour
                minute = picker.minute
                todo = todoInput.text.toString().trim()
                days = selDays.toSet()
                on = true
                snoozeUntil = 0L
            }
            AlarmStore.upsert(this, a)
            AlarmScheduler.schedule(this, a)
            finish()
        }

        findViewById<View>(R.id.testBtn).setOnClickListener {
            AlarmService.start(this, AlarmService.PREVIEW_ID, todoInput.text.toString().trim().ifBlank { "할일 미리보기" })
        }

        findViewById<View>(R.id.deleteBtn).apply {
            visibility = if (editing != null) View.VISIBLE else View.GONE
            setOnClickListener {
                AlertDialog.Builder(this@EditActivity)
                    .setMessage("이 알람을 삭제할까요?")
                    .setPositiveButton("삭제") { _, _ ->
                        editing?.let {
                            AlarmScheduler.cancel(this@EditActivity, it.id)
                            AlarmStore.delete(this@EditActivity, it.id)
                        }
                        finish()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    private fun paintDays() {
        dayButtons.forEachIndexed { i, b ->
            val sel = i in selDays
            b.backgroundTintList = android.content.res.ColorStateList.valueOf(
                getColor(if (sel) R.color.accent else R.color.field)
            )
            b.setTextColor(getColor(if (sel) android.R.color.black else R.color.muted))
        }
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
