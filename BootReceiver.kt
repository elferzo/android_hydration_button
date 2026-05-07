package com.h2owidget.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Восстанавливаем будильник полуночного сброса
            H2OWidget.scheduleMidnightReset(context)

            // Обновляем виджеты (на случай если дата изменилась пока телефон был выключен)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, H2OWidget::class.java)
            )
            for (id in ids) {
                H2OWidget.updateWidget(context, appWidgetManager, id)
            }
        }
    }
}
