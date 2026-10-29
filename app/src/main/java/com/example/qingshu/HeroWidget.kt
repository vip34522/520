package com.example.qingshu

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import android.widget.Toast
import java.util.*

class HeroWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        
        if (action == "com.example.qingshu.ACTION_START" || action == "com.example.qingshu.ACTION_END") {
            val isStart = action == "com.example.qingshu.ACTION_START"
            // 直接打卡，无需密码
            WidgetDataStore.quickMark(context, isStart)
            // 【解决6】明确的 Toast 打卡反馈
            Toast.makeText(context, if (isStart) "已记录：今天来了 🌸" else "已记录：今天走了 ✨", Toast.LENGTH_SHORT).show()
            updateAll(context)
        } else {
            updateAll(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context)
    }

    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context, HeroWidget::class.java))
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_hero)
            val data = WidgetDataStore.load(context)
            val res = PeriodCalc.analyze(data)
            
            val options = manager.getAppWidgetOptions(id)
            var w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            var h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            if (w <= 0) w = 300
            if (h <= 0) h = 150
            
            val density = context.resources.displayMetrics.density
            views.setImageViewBitmap(R.id.iv_hero_bg, drawHero((w * density).toInt(), (h * density).toInt(), res))

            views.setOnClickPendingIntent(R.id.btn_mark_start, PendingIntent.getBroadcast(context, 1, Intent(context, HeroWidget::class.java).apply { action = "com.example.qingshu.ACTION_START" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btn_mark_end, PendingIntent.getBroadcast(context, 2, Intent(context, HeroWidget::class.java).apply { action = "com.example.qingshu.ACTION_END" }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            manager.updateAppWidget(id, views)
        }
    }

    private fun drawHero(w: Int, h: Int, res: PeriodCalc.Result): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.parseColor("#e8507e"), Color.parseColor("#ff9dbb"), Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 30f, 30f, paint)
        paint.shader = null
        paint.color = Color.parseColor("#20FFFFFF")
        c.drawCircle(w * 0.85f, h * 0.1f, w * 0.4f, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = w * 0.045f
        c.drawText(java.text.SimpleDateFormat("MM月dd日 EEEE", Locale.CHINA).format(Date()), w * 0.06f, h * 0.18f, textPaint)

        val statusText = res.statusText
        textPaint.textSize = w * 0.04f
        textPaint.isFakeBoldText = true
        val tw = textPaint.measureText(statusText)
        val rw = tw + w * 0.1f
        val rh = h * 0.12f
        val rr = w * 0.95f
        val rl = rr - rw
        val rt = h * 0.08f
        paint.color = Color.WHITE
        c.drawRoundRect(RectF(rl, rt, rr, rt + rh), rh, rh, paint)
        val sc = if (res.statusType == "period") "#d92d4a" else if (res.statusType == "fertile") "#b86e00" else "#0d8f5f"
        paint.color = Color.parseColor(sc)
        c.drawCircle(rl + rh/2, rt + rh/2, rh*0.15f, paint)
        textPaint.color = Color.parseColor(sc)
        textPaint.textAlign = Paint.Align.CENTER
        c.drawText(statusText, rl + rw/2 + 5f, rt + rh/2 + textPaint.textSize/3, textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        textPaint.color = Color.WHITE
        textPaint.textSize = w * 0.08f
        textPaint.isFakeBoldText = true
        // 【解决1】主文字整体大幅上移，避免与底部按钮重叠
        c.drawText(if (res.isActive) "经期中" else "距下次月经还有 ${res.daysToNext} 天", w * 0.06f, h * 0.40f, textPaint)

        textPaint.textSize = w * 0.042f
        textPaint.color = Color.parseColor("#E0E0E0")
        textPaint.isFakeBoldText = false
        // 【解决1】副标题上移
        c.drawText("预计 ${res.nextStartStr} 来潮 · 平均周期 ${res.avgCycle} 天", w * 0.06f, h * 0.52f, textPaint)

        // 【解决1】进度条上移
        paint.color = Color.parseColor("#40FFFFFF")
        c.drawRoundRect(RectF(w * 0.06f, h * 0.62f, w * 0.94f, h * 0.65f), 20f, 20f, paint)
        paint.color = Color.WHITE
        c.drawRoundRect(RectF(w * 0.06f, h * 0.62f, w * 0.06f + (w * 0.88f * res.progress), h * 0.65f), 20f, 20f, paint)

        return bmp
    }
}
