package com.v2ray.ang.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.ScSwitchActivity

class WidgetProvider : AppWidgetProvider() {
    /**
     * This method is called every time the widget is updated.
     * It updates the widget background based on the V2Ray service running state.
     *
     * @param context The Context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance.
     * @param appWidgetIds The appWidgetIds for which an update is needed.
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pending = goAsync()
        LauncherManager.queryServiceState(context) { state ->
            val running = when (state) {
                AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_STARTING, AppConfig.MSG_STATE_STOPPING -> true
                0, AppConfig.MSG_STATE_NOT_RUNNING -> false
                else -> null
            }
            updateWidgetBackground(context, appWidgetManager, appWidgetIds, running)
        }.invokeOnCompletion { pending.finish() }
    }

    /**
     * Updates the widget background based on whether the V2Ray service is running.
     *
     * @param context The Context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance.
     * @param appWidgetIds The appWidgetIds for which an update is needed.
     * @param isRunning Daemon-acknowledged state, or null when no reliable reply is available.
     */
    private fun updateWidgetBackground(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, isRunning: Boolean?) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_switch)
        // Launch the permission-capable Activity directly from the user's widget interaction.
        val intent = Intent(context, ScSwitchActivity::class.java).apply {
            action = AppConfig.BROADCAST_ACTION_WIDGET_CLICK
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            R.id.layout_switch,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        remoteViews.setOnClickPendingIntent(R.id.layout_switch, pendingIntent)
        remoteViews.setContentDescription(R.id.layout_switch, context.getString(R.string.widget_toggle_vpn))
        if (isRunning == true) {
            remoteViews.setInt(R.id.image_switch, "setImageResource", R.drawable.ic_stop_24dp)
            remoteViews.setInt(R.id.layout_background, "setBackgroundResource", R.drawable.ic_rounded_corner_active)
        } else if (isRunning == false) {
            remoteViews.setInt(R.id.image_switch, "setImageResource", R.drawable.ic_play_24dp)
            remoteViews.setInt(R.id.layout_background, "setBackgroundResource", R.drawable.ic_rounded_corner_inactive)
        }

        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    /**
     * This method is called when the BroadcastReceiver is receiving an Intent broadcast.
     * It handles widget click actions and updates the widget background based on the V2Ray service state.
     *
     * @param context The Context in which the receiver is running.
     * @param intent The Intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (AppConfig.BROADCAST_ACTION_WIDGET_CLICK == intent.action) {
            // Honor pending broadcasts created by versions installed before the Activity switch.
            val pending = goAsync()
            LauncherManager.toggleVService(context, onComplete = { pending.finish() })
        } else if (AppConfig.BROADCAST_ACTION_ACTIVITY == intent.action) {
            AppWidgetManager.getInstance(context)?.let { manager ->
                when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> {
                        updateWidgetBackground(
                            context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)),
                            true
                        )
                    }

                    AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS -> {
                        updateWidgetBackground(
                            context, manager, manager.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java)),
                            false
                        )
                    }
                }
            }
        }
    }
}
