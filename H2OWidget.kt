package com.h2owidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.util.Calendar

class H2OWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TAP            = "com.h2owidget.ACTION_TAP"
        const val ACTION_HOURLY_UPDATE  = "com.h2owidget.ACTION_HOURLY_UPDATE"
        const val ACTION_MIDNIGHT_RESET = "com.h2owidget.ACTION_MIDNIGHT_RESET"
        const val PREFS_NAME            = "H2OWidgetPrefs"
        const val KEY_COUNT             = "count"
        const val KEY_DATE              = "date"

        const val ML_PER_GLASS   = 200
        const val GOAL_ML        = 2000
        const val MAX_GLASSES    = GOAL_ML / ML_PER_GLASS  // 10
        const val DAY_START_HOUR = 8
        const val DAY_END_HOUR   = 21  // окно 13 часов = 780 минут

        /** Расчётное потребление (мл) на текущий момент по линейной зависимости */
        fun expectedMl(): Int {
            val cal = Calendar.getInstance()
            val nowMinutes   = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            val startMinutes = DAY_START_HOUR * 60
            val endMinutes   = DAY_END_HOUR   * 60
            return when {
                nowMinutes <= startMinutes -> 0
                nowMinutes >= endMinutes   -> GOAL_ML
                else -> ((nowMinutes - startMinutes).toFloat() /
                         (endMinutes - startMinutes) * GOAL_ML).toInt()
            }
        }

        fun getTodayString(): String {
            val c = Calendar.getInstance()
            return "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH)}-${c.get(Calendar.DAY_OF_MONTH)}"
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Сброс на новые сутки
            if (prefs.getString(KEY_DATE, "") != getTodayString()) {
                prefs.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, getTodayString()).apply()
            }

            val count      = prefs.getInt(KEY_COUNT, 0)
            val actualMl   = count * ML_PER_GLASS
            val expectedMl = expectedMl()

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Цвет по зонам отставания:
            // факт >= расчёт           → зелёный
            // факт >= расчёт * 0.80    → серый     (0–20% ниже)
            // факт >= расчёт * 0.60    → оранжевый (20–40% ниже)
            // факт <  расчёт * 0.60    → красный   (>40% ниже)
            val bgColor = when {
                expectedMl == 0                    -> Color.parseColor("#4CAF50")
                actualMl >= expectedMl             -> Color.parseColor("#4CAF50")
                actualMl >= expectedMl * 0.80      -> Color.parseColor("#757575")
                actualMl >= expectedMl * 0.60      -> Color.parseColor("#FF9800")
                else                               -> Color.parseColor("#F44336")
            }
            views.setInt(R.id.btn_background, "setBackgroundColor", bgColor)

            views.setTextViewText(R.id.tv_label, "H\u2082O")  // H₂O — unicode subscript 2
            views.setTextViewText(R.id.tv_count, if (count >= MAX_GLASSES) "✓" else "$count")

            // Обработчик нажатия
            val tapPending = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, H2OWidget::class.java).apply { action = ACTION_TAP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_background, tapPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Будильник на начало следующего часа */
        fun scheduleHourlyUpdate(context: Context) {
            val nextHour = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            setAlarm(context, 2, ACTION_HOURLY_UPDATE, nextHour.timeInMillis)
        }

        /** Будильник на полночь */
        fun scheduleMidnightReset(context: Context) {
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            setAlarm(context, 1, ACTION_MIDNIGHT_RESET, midnight.timeInMillis)
        }

        private fun setAlarm(context: Context, reqCode: Int, action: String, triggerMs: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, reqCode,
                Intent(context, H2OWidget::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
        scheduleHourlyUpdate(context)
        scheduleMidnightReset(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val awm = AppWidgetManager.getInstance(context)
        val ids = awm.getAppWidgetIds(ComponentName(context, H2OWidget::class.java))

        when (intent.action) {
            ACTION_TAP -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.getString(KEY_DATE, "") != getTodayString()) {
                    prefs.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, getTodayString()).apply()
                }
                val cur = prefs.getInt(KEY_COUNT, 0)
                if (cur < MAX_GLASSES) prefs.edit().putInt(KEY_COUNT, cur + 1).apply()
                for (id in ids) updateWidget(context, awm, id)
            }
            ACTION_HOURLY_UPDATE -> {
                for (id in ids) updateWidget(context, awm, id)
                scheduleHourlyUpdate(context)
            }
            ACTION_MIDNIGHT_RESET -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, getTodayString()).apply()
                for (id in ids) updateWidget(context, awm, id)
                scheduleMidnightReset(context)
                scheduleHourlyUpdate(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleHourlyUpdate(context)
        scheduleMidnightReset(context)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for ((req, action) in listOf(1 to ACTION_MIDNIGHT_RESET, 2 to ACTION_HOURLY_UPDATE)) {
            am.cancel(PendingIntent.getBroadcast(
                context, req,
                Intent(context, H2OWidget::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
    }
}
