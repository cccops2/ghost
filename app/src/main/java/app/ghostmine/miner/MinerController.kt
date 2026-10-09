package app.ghostmine.miner

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.ghostmine.core.CustomSettings
import app.ghostmine.core.Mode
import app.ghostmine.core.Sample
import app.ghostmine.core.Session
import app.ghostmine.core.formatHashrate
import app.ghostmine.data.Repo
import app.ghostmine.hardware.HardwareMonitor
import app.ghostmine.notify.Notifier
import app.ghostmine.service.MiningService
import app.ghostmine.stratum.Job
import app.ghostmine.stratum.PoolState
import app.ghostmine.stratum.StratumClient
import app.ghostmine.stratum.StratumListener
import app.ghostmine.stratum.parseEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class MiningState(
    val running: Boolean = false,
    val hashrate: Double = 0.0,
    val totalHashes: Long = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val pool: PoolState = PoolState.IDLE,
    val poolMessage: String = "",
    val pingMs: Long = -1,
    val threads: Int = 0,
    val cpuLimit: Int = 0,
    val effectiveLimit: Int = 0,
    val startedAt: Long = 0,
    val tempC: Float = Float.NaN,
    val netDifficulty: Double = 0.0,
    val estBtc: Double = 0.0,
    val throttled: Boolean = false,
    val history: List<Double> = emptyList(),
)

/** Owns the engine + Stratum client, thermal/battery protection and session bookkeeping. UI only observes [state]. */
object MinerController : StratumListener {
    private const val BLOCK_REWARD = 3.125 // BTC per block (post-2024 halving); fees ignored

    private val _state = MutableStateFlow(MiningState())
    val state: StateFlow<MiningState> = _state.asStateFlow()

    private lateinit var app: Context
    private var engine: MinerEngine? = null
    private var client: StratumClient? = null
    private var exec: ScheduledExecutorService? = null
    private var wake: PowerManager.WakeLock? = null
    private var cfg = HardwareMonitor.Rec(1, 30, 38)
    private var effLimit = 30
    private var lastHashes = 0L
    private var lastTick = 0L
    private var hashrate = 0.0
    private var tickCount = 0
    private var startedAt = 0L
    private var lastSampleTs = 0L
    private val hist = ArrayDeque<Double>()
    @Volatile private var netDiff = 0.0
    private val accepted = AtomicInteger(0)
    private val rejected = AtomicInteger(0)
    @Volatile private var wasConnected = false
    @Volatile private var everConnected = false

    fun init(ctx: Context) { app = ctx.applicationContext }

    fun currentConfig(): HardwareMonitor.Rec {
        val info = HardwareMonitor.info(app)
        return if (Repo.mode == Mode.CUSTOM) {
            val c = Repo.custom
            HardwareMonitor.Rec(
                c.threads.coerceIn(1, info.cores), c.cpuLimit.coerceIn(10, 100),
                c.maxTemp.coerceIn(35, HardwareMonitor.HARD_CAP_C.toInt()), c.onlyCharging
            )
        } else HardwareMonitor.recommend(info, Repo.mode)
    }

    @Synchronized
    fun start(): String? {
        if (_state.value.running) return null
        val pool = Repo.activePool() ?: return "Add a mining pool first."
        val ep = parseEndpoint(pool.url) ?: return "Invalid pool URL. Use stratum+tcp://host:port"
        val user = pool.worker.ifBlank { if (Repo.address.isNotBlank()) Repo.address + ".ghost" else "" }
        if (user.isBlank()) return "Set a worker name in the pool, or your Bitcoin address in Wallet."
        cfg = currentConfig()
        val live = HardwareMonitor.live(app)
        if (cfg.onlyCharging && !live.charging) return "Custom mode requires the charger to be connected."

        accepted.set(0); rejected.set(0); netDiff = 0.0; hashrate = 0.0; tickCount = 0
        hist.clear(); wasConnected = false; everConnected = false

        val c = StratumClient(ep, user, pool.password, this)
        val e = MinerEngine { id, en2, nt, no -> c.submit(id, en2, nt, no) }
        client = c; engine = e
        effLimit = cfg.cpuLimit
        e.cpuLimit = effLimit
        e.start(cfg.threads)
        c.start()

        startedAt = System.currentTimeMillis()
        lastSampleTs = startedAt
        lastTick = System.nanoTime()
        lastHashes = 0
        val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ghostmine:mining").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
        _state.value = MiningState(
            running = true, threads = cfg.threads, cpuLimit = cfg.cpuLimit, effectiveLimit = effLimit,
            startedAt = startedAt, pool = PoolState.CONNECTING, tempC = live.tempC
        )
        exec = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ try { tick() } catch (t: Throwable) { } }, 1, 1, TimeUnit.SECONDS)
        }
        ContextCompat.startForegroundService(app, Intent(app, MiningService::class.java))
        Notifier.alert(app, Notifier.ID_STARTED, "Mining started", "Ghost Mine is running.")
        return null
    }

    @Synchronized
    fun stop(reason: String? = null) {
        val s = _state.value
        if (!s.running) return
        exec?.shutdownNow(); exec = null
        engine?.stop(); client?.stop()
        engine = null; client = null
        try { wake?.release() } catch (e: Exception) { }
        wake = null
        val end = System.currentTimeMillis()
        val durSec = (end - startedAt) / 1000
        if (durSec >= 5) {
            Repo.addSession(Session(startedAt, end, s.totalHashes / maxOf(1L, durSec).toDouble(), s.accepted, s.rejected, s.estBtc))
            Repo.addSample(Sample(end, s.hashrate))
        }
        _state.value = MiningState()
        app.stopService(Intent(app, MiningService::class.java))
        Notifier.cancelOngoing(app)
        Notifier.alert(app, Notifier.ID_STOPPED, "Mining stopped", reason ?: "Session ended.")
    }

    /** Re-apply the current mode/custom settings to a running session. */
    @Synchronized
    fun reconfigure() {
        val e = engine ?: return
        if (!_state.value.running) return
        cfg = currentConfig()
        effLimit = cfg.cpuLimit
        e.cpuLimit = effLimit
        e.start(cfg.threads)
        _state.update { it.copy(threads = cfg.threads, cpuLimit = cfg.cpuLimit, effectiveLimit = effLimit, throttled = false) }
    }

    private fun tick() {
        val e = engine ?: return
        val now = System.nanoTime()
        val total = e.totalHashes.get()
        val dt = (now - lastTick) / 1e9
        if (dt <= 0) return
        val inst = (total - lastHashes) / dt
        lastTick = now; lastHashes = total
        hashrate = if (hashrate == 0.0) inst else hashrate * 0.6 + inst * 0.4
        tickCount++

        var throttled = _state.value.throttled
        var temp = _state.value.tempC
        if (tickCount % 2 == 0) {
            val live = HardwareMonitor.live(app)
            temp = live.tempC
            val hardHot = (!temp.isNaN() && temp >= HardwareMonitor.HARD_CAP_C) ||
                live.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            if (hardHot) { stop("Temperature too high. Stopped to protect your device."); return }
            val warm = (!temp.isNaN() && temp >= cfg.maxTemp) || live.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
            if (warm) {
                if (!throttled) Notifier.alert(app, Notifier.ID_HOT, "Temperature high", "Intensity reduced to cool down.")
                effLimit = maxOf(10, effLimit - 15)
                throttled = true
            } else if (effLimit < cfg.cpuLimit && (temp.isNaN() || temp <= cfg.maxTemp - 3) &&
                live.thermalStatus < PowerManager.THERMAL_STATUS_MODERATE
            ) {
                effLimit = minOf(cfg.cpuLimit, effLimit + 5)
                throttled = effLimit < cfg.cpuLimit
            }
            e.cpuLimit = effLimit
            if (Repo.stopLowBattery && !live.charging && live.batteryPct in 0..15) {
                Notifier.alert(app, Notifier.ID_BATT, "Battery too low", "Mining stopped at ${live.batteryPct}%.")
                stop("Battery too low"); return
            }
            if (cfg.onlyCharging && !live.charging) { stop("Charger disconnected"); return }
        }

        hist.addLast(hashrate)
        if (hist.size > 60) hist.removeFirst()
        val est = if (netDiff > 0) total * BLOCK_REWARD / (netDiff * 4294967296.0) else 0.0
        _state.update {
            it.copy(
                hashrate = hashrate, totalHashes = total, threads = cfg.threads, cpuLimit = cfg.cpuLimit,
                effectiveLimit = effLimit, tempC = temp, estBtc = est, throttled = throttled,
                history = hist.toList(), netDifficulty = netDiff
            )
        }
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastSampleTs >= 300_000) { Repo.addSample(Sample(nowMs, hashrate)); lastSampleTs = nowMs }
        if (tickCount % 10 == 0) {
            Notifier.updateOngoing(app, "${formatHashrate(hashrate)} · ${accepted.get()} shares accepted")
        }
    }

    // ---- Stratum callbacks (socket thread) ----
    override fun onState(state: PoolState, message: String) {
        if (!_state.value.running) return
        _state.update { it.copy(pool = state, poolMessage = message) }
        if (state == PoolState.CONNECTED) {
            if (everConnected && !wasConnected) Notifier.alert(app, Notifier.ID_POOL, "Pool reconnected", "Mining resumed.")
            everConnected = true; wasConnected = true
        } else if (wasConnected && (state == PoolState.ERROR || state == PoolState.CONNECTING)) {
            wasConnected = false
            Notifier.alert(app, Notifier.ID_POOL, "Pool disconnected", message.ifBlank { "Trying to reconnect…" })
        }
    }

    override fun onSubscribed(extranonce1: String, extranonce2Size: Int) { engine?.setSubscription(extranonce1, extranonce2Size) }
    override fun onDifficulty(difficulty: Double) { engine?.setDifficulty(difficulty) }
    override fun onJob(job: Job) { engine?.setJob(job); netDiff = difficultyFromNbits(job.nbits) }

    override fun onShare(accepted: Boolean, reason: String?) {
        if (accepted) this.accepted.incrementAndGet() else this.rejected.incrementAndGet()
        _state.update { it.copy(accepted = this.accepted.get(), rejected = this.rejected.get()) }
    }

    override fun onPing(ms: Long) { _state.update { it.copy(pingMs = ms) } }

    fun defaultCustom(ctx: Context): CustomSettings {
        val r = HardwareMonitor.recommend(HardwareMonitor.info(ctx), Mode.BALANCED)
        return CustomSettings(r.threads, r.cpuLimit, r.maxTemp, false)
    }
}
