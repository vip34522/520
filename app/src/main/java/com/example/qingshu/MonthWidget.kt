package com.example.qingshu

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

class MonthWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "PREV") { WidgetDataStore.setMonthOffset(context, WidgetDataStore.getMonthOffset(context) - 1); updateAll(context) }
        else if (intent.action == "NEXT") { WidgetDataStore.setMonthOffset(context, WidgetDataStore.getMonthOffset(context) + 1); updateAll(context) }
        else if (intent.action == "TODAY") { WidgetDataStore.setMonthOffset(context, 0); updateAll(context) }
        else if (intent.action == "com.example.qingshu.ACTION_M_START") { WidgetDataStore.quickMark(context, true); updateAll(context) }
        else if (intent.action == "com.example.qingshu.ACTION_M_END") { WidgetDataStore.quickMark(context, false); updateAll(context) }
        else { updateAll(context) }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context, MonthWidget::class.java))
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_month)
            val data = WidgetDataStore.load(context)
            val offset = WidgetDataStore.getMonthOffset(context)
            
            val cal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
            views.setTextViewText(R.id.tv_month_title, "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH)+1}月")

            val options = manager.getAppWidgetOptions(id)
            var w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            var h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (w <= 0) w = 320
            if (h < 380) h = 380
            
            val density = context.resources.displayMetrics.density
            // 因为底部打卡按钮高度变了，画布高度稍微调整
            views.setImageViewBitmap(R.id.iv_month_bg, drawMonth((w * density).toInt(), ((h - 80) * density).toInt(), data, offset))

            views.setOnClickPendingIntent(R.id.btn_prev_month, PendingIntent.getBroadcast(context, 1, Intent(context, MonthWidget::class.java).apply { action = "PREV" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btn_next_month, PendingIntent.getBroadcast(context, 2, Intent(context, MonthWidget::class.java).apply { action = "NEXT" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btn_today_month, PendingIntent.getBroadcast(context, 3, Intent(context, MonthWidget::class.java).apply { action = "TODAY" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btn_m_mark_start, PendingIntent.getBroadcast(context, 4, Intent(context, MonthWidget::class.java).apply { action = "com.example.qingshu.ACTION_M_START" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btn_m_mark_end, PendingIntent.getBroadcast(context, 5, Intent(context, MonthWidget::class.java).apply { action = "com.example.qingshu.ACTION_M_END" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            manager.updateAppWidget(id, views)
        }
    }

    private fun drawMonth(w: Int, h: Int, data: JSONObject?, offset: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#FDF6F8"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, offset) }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val today = Calendar.getInstance()
        val paddingX = w * 0.04f
        val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val cellW = (w - paddingX * 2) / 7f
        
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = cellW * 0.3f
        textPaint.color = Color.parseColor("#93889B")
        val weekHeaderH = h * 0.08f
        for (i in 0 until 7) c.drawText(weekNames[i], paddingX + cellW * i + cellW / 2f, weekHeaderH / 2f + textPaint.textSize/3, textPaint)
        
        val firstDay = Calendar.getInstance().apply { set(year, month - 1, 1) }
        val startPad = if (firstDay.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6 else firstDay.get(Calendar.DAY_OF_WEEK) - 2
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = ceil((startPad + daysInMonth) / 7.0).toInt()
        val gridStartY = weekHeaderH
        val cellH = (h - gridStartY) / rows
        
        val dayMap = mutableMapOf<String, String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val res = PeriodCalc.analyze(data)
        
        if (data != null) {
            val periods = data.optJSONArray("periods")
            if (periods != null) {
                for (i in 0 until periods.length()) {
                    val p = periods.getJSONObject(i)
                    val stStr = p.optString("start")
                    val enStr = p.optString("end")
                    try {
                        val st = sdf.parse(stStr)
                        val en = if (enStr.isNotEmpty() && enStr != "null") sdf.parse(enStr) else st
                        if (st != null) {
                            val endT = (en ?: st).time
                            var curT = st.time
                            while (curT <= endT) {
                                dayMap[sdf.format(Date(curT))] = "period"
                                curT += 86400000L
                            }
                        }
                    } catch(e:Exception){}
                }
            }
        }
        
        val nextStartCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, if (res.daysToNext > 0) res.daysToNext else 0) }
        val nextStartKey = sdf.format(nextStartCal.time)
        
        val predEndCal = Calendar.getInstance().apply { time = nextStartCal.time; add(Calendar.DAY_OF_YEAR, res.avgPeriod - 1) }
        val predEndKey = sdf.format(predEndCal.time)

        val fertileStartCal = Calendar.getInstance().apply { time = nextStartCal.time; add(Calendar.DAY_OF_YEAR, -19) }
        val fertileEndCal = Calendar.getInstance().apply { time = nextStartCal.time; add(Calendar.DAY_OF_YEAR, -13) }
        val fertileStartKey = sdf.format(fertileStartCal.time)
        val fertileEndKey = sdf.format(fertileEndCal.time)

        for (r in 0 until rows) {
            for (col in 0 until 7) {
                val dayIndex = r * 7 + col - startPad
                if (dayIndex < 0 || dayIndex >= daysInMonth) continue
                
                val d = dayIndex + 1
                val dateCal = Calendar.getInstance().apply { set(year, month - 1, d) }
                val dateKey = sdf.format(dateCal.time)
                val cx = paddingX + cellW * col + cellW / 2f
                val cy = gridStartY + cellH * r + cellH / 2f
                val isToday = (year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) + 1 && d == today.get(Calendar.DAY_OF_MONTH))
                
                var state = "none" // 默认无标记
                if (dayMap.containsKey(dateKey)) {
                    state = "period"
                } else if (dateKey >= nextStartKey && dateKey <= predEndKey) { // 预测多天
                    state = "predicted"
                } else if (dateKey >= fertileStartKey && dateKey <= fertileEndKey) {
                    state = "fertile"
                }
                
                if (state == "period") {
                    paint.color = Color.parseColor("#E8507E"); paint.style = Paint.Style.FILL
                    c.drawCircle(cx, cy, cellW * 0.35f, paint)
                    textPaint.color = Color.WHITE
                } else if (state == "fertile") {
                    paint.color = Color.parseColor("#F59E0B"); paint.style = Paint.Style.FILL
                    c.drawCircle(cx, cy, cellW * 0.35f, paint)
                    textPaint.color = Color.WHITE
                } else if (state == "predicted") {
                    paint.color = Color.parseColor("#E8507E"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                    c.drawCircle(cx, cy, cellW * 0.35f, paint)
                    textPaint.color = Color.parseColor("#E8507E")
                } else {
                    // 无标记，黑字
                    textPaint.color = Color.parseColor("#2B2030")
                }
                
                if (isToday) {
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; paint.color = Color.parseColor("#FF9DBB")
                    c.drawCircle(cx, cy, cellW * 0.42f, paint)
                }
                
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = cellW * 0.35f
                textPaint.isFakeBoldText = isToday || state == "period" || state == "fertile"
                c.drawText(d.toString(), cx, cy + textPaint.textSize/3, textPaint)
            }
        }
        
        // 底部图例 (去掉了安全)
        val legendY = h - 15f
        textPaint.textSize = cellW * 0.22f
        textPaint.isFakeBoldText = false
        textPaint.textAlign = Paint.Align.LEFT
        var lx = paddingX
        
        fun drawLegend(colorStr: String, label: String, isStroke: Boolean) {
            paint.style = if (isStroke) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = 2f
            paint.color = Color.parseColor(colorStr)
            c.drawCircle(lx + 10f, legendY - 5f, cellW * 0.1f, paint)
            textPaint.color = Color.parseColor("#93889B")
            c.drawText(label, lx + 20f, legendY, textPaint)
            lx += w / 3f // 3个图例均分
        }
        
        drawLegend("#E8507E", "经期", false)
        drawLegend("#E8507E", "预测", true)
        drawLegend("#F59E0B", "易孕", false)
        
        return bmp
    }
}
