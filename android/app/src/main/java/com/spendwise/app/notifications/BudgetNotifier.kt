package com.spendwise.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spendwise.app.R
import com.spendwise.app.core.Money
import com.spendwise.app.data.local.Category
import com.spendwise.app.data.repo.AlertLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.budget_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Returns false if it could not be shown (permission denied), so the caller does NOT record it as delivered. */
    @SuppressLint("MissingPermission") // checked just below
    fun notify(category: Category, level: AlertLevel, spentMinor: Long, limitMinor: Long): Boolean {
        val nm = NotificationManagerCompat.from(context)
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !nm.areNotificationsEnabled()) return false

        val title = if (level == AlertLevel.OVER) "${category.label} budget exceeded" else "${category.label} budget at 80%"
        val text = "${Money.format(spentMinor)} of ${Money.format(limitMinor)} spent this month"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(category.ordinal * 10 + level.ordinal, n)
        return true
    }

    private companion object { const val CHANNEL = "budget_alerts" }
}
