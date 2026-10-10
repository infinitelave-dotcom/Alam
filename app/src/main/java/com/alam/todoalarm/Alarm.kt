package com.alam.todoalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

val DAY_NAMES = listOf("일", "월", "화", "수", "목", "금", "토")

/** days: 0=일요일 … 6=토요일. 비어 있으면 1회 알람. */
data class Alarm(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var todo: String,
    var days: Set<Int>,
    var on: Boolean = true,
    var snoozeUntil: Long = 0L,
) {
    val timeText get() = "%02d:%02d".format(hour, minute)

    val daysText
        get() = when {
            days.size == 7 -> "매일"
            days.isEmpty() -> "1회"
            else -> days.sorted().joinToString(" ") { DAY_NAMES[it] }
        }

    fun toJson() = JSONObject().apply {
        put("id", id); put("hour", hour); put("minute", minute); put("todo", todo)
        put("days", JSONArray(days.sorted())); put("on", on); put("snoozeUntil", snoozeUntil)
    }

    companion object {
        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            todo = o.optString("todo"),
            days = o.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            on = o.optBoolean("on", true),
            snoozeUntil = o.optLong("snoozeUntil", 0L),
        )
    }
}

object AlarmStore {
    private const val PREFS = "alarms"
    private const val KEY = "list"

    fun all(ctx: Context): MutableList<Alarm> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(ctx: Context, list: List<Alarm>) {
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun get(ctx: Context, id: Int) = all(ctx).find { it.id == id }

    fun upsert(ctx: Context, alarm: Alarm) {
        val list = all(ctx)
        val i = list.indexOfFirst { it.id == alarm.id }
        if (i >= 0) list[i] = alarm else list.add(alarm)
        saveAll(ctx, list)
    }

    fun delete(ctx: Context, id: Int) = saveAll(ctx, all(ctx).filter { it.id != id })

    fun nextId(ctx: Context) = (all(ctx).maxOfOrNull { it.id } ?: 0) + 1
}
