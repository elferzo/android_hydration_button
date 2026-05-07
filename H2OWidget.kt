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
        const val ACTION_TAP = "com.h2owidget.ACTION_TAP"
        const val ACTION_MIDNIGHT_RESET = "com.h2owidget.ACTION_MIDNIGHT_RESET"
        const val PREFS_NAME = "H2OWidgetPrefs"
        const val KEY_COUNT = "count"
        const val KEY_DATE = "date"
        const val MAX_COUNT = 10

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // Сброс если новые сутки
            val today = getTodayString()
            val savedDate = prefs.getString(KEY_DATE, "")
            if (savedDate != today) {
                prefs.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, today).apply()
            }

            val count = prefs.getInt(KEY_COUNT, 0)
            val done = count >= MAX_COUNT

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (done) {
                // Зелёная кнопка без счётчика
                views.setInt(R.id.btn_background, "setBackgroundColor", Color.parseColor("#4CAF50"))
                views.setTextViewText(R.id.tv_label, "H2O ✓")
                views.setTextViewText(R.id.tv_count, "")
            } else {
                // Обычный вид: надпись + счётчик
                views.setInt(R.id.btn_background, "setBackgroundColor", Color.parseColor("#1E88E5"))
                views.setTextViewText(R.id.tv_label, "H2O")
                views.setTextViewText(R.id.tv_count, "$count")
            }

            // Обработчик нажатия
            val tapIntent = Intent(context, H2OWidget::class.java).apply {
                action = ACTION_TAP
            }
            val tapPending = PendingIntent.getBroadcast(
                context, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_background, tapPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun getTodayString(): String {
            val cal = Calendar.getInstance()
            return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }

        fun scheduleMidnightReset(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, H2OWidget::class.java).apply {
                action = ACTION_MIDNIGHT_RESET
            }
            val pending = PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Следующая полночь
            val midnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    midnight.timeInMillis,
                    pending
                )
            } catch (e: SecurityException) {
                // Нет разрешения SCHEDULE_EXACT_ALARM — fallback на неточный будильник
                alarmManager.set(AlarmManager.RTC_WAKEUP, midnight.timeInMillis, pending)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
        scheduleMidnightReset(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, H2OWidget::class.java)
        )

        when (intent.action) {
            ACTION_TAP -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val today = getTodayString()
                val savedDate = prefs.getString(KEY_DATE, "")
                if (savedDate != today) {
                    prefs.edit().putInt(KEY_COUNT, 0).putString(KEY_DATE, today).apply()
                }
                val current = prefs.getInt(KEY_COUNT, 0)
                if (current < MAX_COUNT) {
                    prefs.edit().putInt(KEY_COUNT, current + 1).apply()
                }
                for (id in ids) updateWidget(context, appWidgetManager, id)
            }

            ACTION_MIDNIGHT_RESET -> {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putInt(KEY_COUNT, 0)
                    .putString(KEY_DATE, getTodayString())
                    .apply()
                for (id in ids) updateWidget(context, appWidgetManager, id)
                // Планируем следующий сброс
                scheduleMidnightReset(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        scheduleMidnightReset(context)
    }

    override fun onDisabled(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, H2OWidget::class.java).apply {
            action = ACTION_MIDNIGHT_RESET
        }
        val pending = PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}
