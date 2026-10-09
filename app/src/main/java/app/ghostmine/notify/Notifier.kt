package app.ghostmine.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.ghostmine.MainActivity
import app.ghostmine.R
import app.ghostmine.data.Repo
import app.ghostmine.service.MiningService

object Notifier {
    private const val CH_ONGOING = "mining"
    private const val CH_ALERTS = "alerts"
    const val ID_ONGOING = 1
    const val ID_STARTED = 2
    const val ID_STOPPED = 3
    const val ID_POOL = 4
    const val ID_HOT = 5
    const val ID_BATT = 6
    const val ID_PAY = 7

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "Mining status", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_ALERTS, "Mining alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun openIntent(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun ongoing(ctx: Context, text: String): Notification {
        val stop = PendingIntent.getService(
            ctx, 1, Intent(ctx, MiningService::class.java).setAction(MiningService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setContentTitle("Ghost Mine")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(ctx))
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun updateOngoing(ctx: Context, text: String) {
        if (!canPost(ctx)) return
        try { NotificationManagerCompat.from(ctx).notify(ID_ONGOING, ongoing(ctx, text)) } catch (e: SecurityException) { }
    }

    fun cancelOngoing(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_ONGOING)
    }

    fun alert(ctx: Context, id: Int, title: String, text: String) {
        if (!Repo.notifOn || !canPost(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (e: SecurityException) { }
    }

    /** Ready for a pool-API integration; nothing calls it yet because plain Stratum does not report payouts. */
    fun paymentReceived(ctx: Context, btc: String) =
        alert(ctx, ID_PAY, "Payment received", "$btc BTC sent to your address.")
}
