package app.ghostmine.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import app.ghostmine.miner.MinerController
import app.ghostmine.notify.Notifier

/** Foreground service that keeps the process alive while mining (Android requires the visible notification). */
class MiningService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MinerController.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, Notifier.ID_ONGOING, Notifier.ongoing(this, "Mining…"),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        return START_NOT_STICKY
    }

    companion object { const val ACTION_STOP = "app.ghostmine.STOP" }
}
