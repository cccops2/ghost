package app.ghostmine.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

object HardwareMonitor {
    data class Info(
        val model: String,
        val soc: String,
        val cores: Int,
        val abi: String,
        val ramTotalMb: Long,
        val ramFreeMb: Long,
        val weak: Boolean,
    )

    data class Live(val tempC: Float, val batteryPct: Int, val charging: Boolean, val thermalStatus: Int)

    data class Rec(val threads: Int, val cpuLimit: Int, val maxTemp: Int, val onlyCharging: Boolean = false)

    const val HARD_CAP_C = 45f

    fun info(ctx: Context): Info {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val cores = Runtime.getRuntime().availableProcessors()
        val totalMb = mi.totalMem / (1024 * 1024)
        val soc = if (Build.VERSION.SDK_INT >= 31) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != "unknown" }
                .joinToString(" ").ifBlank { Build.HARDWARE }
        } else Build.HARDWARE
        return Info(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            soc = soc,
            cores = cores,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            ramTotalMb = totalMb,
            ramFreeMb = mi.availMem / (1024 * 1024),
            weak = cores <= 4 || totalMb < 3000,
        )
    }

    fun live(ctx: Context): Live {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        var thermal = 0
        if (Build.VERSION.SDK_INT >= 29) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            thermal = pm.currentThermalStatus
        }
        return Live(if (t > 0) t / 10f else Float.NaN, pct, charging, thermal)
    }

    fun recommend(info: Info, mode: app.ghostmine.core.Mode): Rec {
        val c = info.cores
        val w = info.weak
        return when (mode) {
            app.ghostmine.core.Mode.ECO -> Rec(maxOf(1, c / 4), if (w) 20 else 30, 38)
            app.ghostmine.core.Mode.BALANCED -> Rec(maxOf(1, c / 2), if (w) 35 else 55, 41)
            app.ghostmine.core.Mode.PERFORMANCE -> Rec(maxOf(1, if (w) c / 2 else c - 1), if (w) 60 else 85, 44)
            app.ghostmine.core.Mode.CUSTOM -> Rec(maxOf(1, c / 2), 50, 41)
        }
    }
}
