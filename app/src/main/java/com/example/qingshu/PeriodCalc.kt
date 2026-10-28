package com.example.qingshu

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object PeriodCalc {
    data class Result(
        val statusType: String, val statusText: String, val daysToNext: Int, 
        val nextStartStr: String, val avgCycle: Int, val progress: Float, val isActive: Boolean
    )

    fun analyze(data: JSONObject?): Result {
        val default = Result("safe", "安全期", 0, "--", 28, 0f, false)
        if (data == null) return default
        val periodsArr = data.optJSONArray("periods") ?: return default
        val periods = mutableListOf<Pair<Date, Date?>>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Date()

        for (i in 0 until periodsArr.length()) {
            val p = periodsArr.getJSONObject(i)
            val start = sdf.parse(p.optString("start")) ?: continue
            val endStr = p.optString("end", "")
            val end = if (endStr.isNotEmpty() && endStr != "null") sdf.parse(endStr) else null
            periods.add(Pair(start, end))
        }
        if (periods.isEmpty()) return default
        periods.sortBy { it.first }

        val cycles = mutableListOf<Int>()
        for (i in 1 until periods.size) {
            val diff = ((periods[i].first.time - periods[i-1].first.time) / 86400000L).toInt()
            if (diff in 15..75) cycles.add(diff)
        }
        val avgCycle = if (cycles.isNotEmpty()) cycles.takeLast(6).average().toInt() else 28

        val active = periods.firstOrNull { it.second == null && today >= it.first }
        val cal = Calendar.getInstance().apply { time = periods.last().first; add(Calendar.DAY_OF_YEAR, avgCycle) }
        val next = cal.time
        val daysToNext = ((next.time - today.time) / 86400000L).toInt()
        val daysSince = ((today.time - periods.last().first.time) / 86400000L).toInt()
        val progress = (daysSince.toFloat() / avgCycle).coerceIn(0f, 1f)
        val nextStr = SimpleDateFormat("MM月dd日", Locale.CHINA).format(next)
        
        var type = "safe"
        var text = "安全期"
        
        if (active != null) {
            type = "period"
            text = "月经期"
        } else if (daysToNext in 13..19) {
            type = "fertile"
            text = "易孕期"
        }

        return Result(type, text, daysToNext, nextStr, avgCycle, progress, active != null)
    }
}
