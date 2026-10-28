package com.example.qingshu

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object WidgetDataStore {
    private const val PREFS = "qingshu_data"
    private const val KEY = "qingshu_period_v1"

    fun save(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
        refreshWidgets(context)
    }

    fun load(context: Context): JSONObject? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try { JSONObject(s) } catch (e: Exception) { null }
    }

    fun quickMark(context: Context, isStart: Boolean) {
        val data = load(context) ?: return
        val periods = data.optJSONArray("periods") ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        try {
            var updated = false
            if (isStart) {
                var exists = false
                for (i in 0 until periods.length()) {
                    if (periods.getJSONObject(i).optString("start") == today) exists = true
                }
                if (!exists) {
                    val newPeriod = JSONObject().apply {
                        put("id", "p" + System.currentTimeMillis())
                        put("start", today)
                        put("end", JSONObject.NULL)
                    }
                    periods.put(newPeriod)
                    updated = true
                }
            } else {
                for (i in periods.length() - 1 downTo 0) {
                    val p = periods.getJSONObject(i)
                    if (p.isNull("end") || p.optString("end").isEmpty()) {
                        p.put("end", today)
                        updated = true
                        break
                    }
                }
            }
            if (updated) {
                data.put("periods", periods)
                save(context, data.toString())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun refreshWidgets(context: Context) {
        val heroIntent = Intent(context, HeroWidget::class.java).apply { action = "android.appwidget.action.APPWIDGET_UPDATE" }
        context.sendBroadcast(heroIntent)
        val monthIntent = Intent(context, MonthWidget::class.java).apply { action = "android.appwidget.action.APPWIDGET_UPDATE" }
        context.sendBroadcast(monthIntent)
    }
    
    fun getMonthOffset(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("month_offset", 0)
    fun setMonthOffset(context: Context, offset: Int) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("month_offset", offset).apply() }
}
