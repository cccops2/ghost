package app.ghostmine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ghostmine.core.CustomSettings
import app.ghostmine.core.Mode
import app.ghostmine.core.PoolConfig
import app.ghostmine.core.formatBtc
import app.ghostmine.core.formatDuration
import app.ghostmine.core.formatHashrate
import app.ghostmine.data.Repo
import app.ghostmine.hardware.HardwareMonitor
import app.ghostmine.miner.MinerController
import app.ghostmine.stratum.parseEndpoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun SettingsScreen(onBack: () -> Unit, onPool: () -> Unit, onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember { HardwareMonitor.info(ctx) }
    var live by remember { mutableStateOf(HardwareMonitor.live(ctx)) }
    LaunchedEffect(Unit) { while (true) { live = HardwareMonitor.live(ctx); delay(5000) } }
    val rec = HardwareMonitor.recommend(info, Mode.BALANCED)

    OverlayPage("Settings", onBack) {
        GCard(Modifier.fillMaxWidth(), onClick = onPool) { Body("Mining Pool"); Label("Stratum servers and worker") }
        GCard(Modifier.fillMaxWidth(), onClick = onHistory) { Body("Mining History"); Label("Past sessions") }

        GCard(Modifier.fillMaxWidth()) {
            Label("Device")
            Spacer(Modifier.height(10.dp))
            MetricGrid(
                listOf(
                    "Model" to info.model,
                    "Processor" to info.soc,
                    "Cores" to "${info.cores}",
                    "Architecture" to info.abi,
                    "Memory" to "${info.ramFreeMb} / ${info.ramTotalMb} MB",
                    "Battery" to (if (live.batteryPct >= 0) "${live.batteryPct}%" else "--") + (if (live.charging) " · charging" else ""),
                )
            )
            Spacer(Modifier.height(14.dp))
            Label(
                "Recommended: ${rec.threads} thread(s) · ${rec.cpuLimit}% CPU · ${rec.maxTemp} °C limit" +
                    if (info.weak) " (lighter profile for this device)" else ""
            )
            Spacer(Modifier.height(10.dp))
            Body("Apply recommended", color = G.Accent, size = 14, modifier = Modifier.clickable {
                Repo.setMode(Mode.BALANCED)
                Repo.setCustom(CustomSettings(rec.threads, rec.cpuLimit, rec.maxTemp, false))
                MinerController.reconfigure()
            }.padding(vertical = 6.dp))
        }

        GCard(Modifier.fillMaxWidth()) {
            SwitchRow("Notifications", "Start, stop, pool and temperature alerts", Repo.notifOn) { Repo.setNotif(it) }
            Spacer(Modifier.height(14.dp))
            SwitchRow("Stop at low battery", "Stops mining at 15% when not charging", Repo.stopLowBattery) { Repo.setStopLowBattery(it) }
        }

        GCard(Modifier.fillMaxWidth()) {
            Label("Safety")
            Spacer(Modifier.height(6.dp))
            Body("Mining is hard-stopped at ${HardwareMonitor.HARD_CAP_C.toInt()} °C battery temperature or a severe system thermal state. Pool traffic can use TLS (stratum+ssl://). Credentials are stored encrypted on this device.", color = G.Dim, size = 13)
        }
        Label("Ghost Mine 1.0 · CPU SHA-256d · Stratum V1")
    }
}

@Composable
fun PoolScreen(onBack: () -> Unit) {
    val st by MinerController.state.collectAsState()
    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var worker by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var minPay by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun open(p: PoolConfig?) {
        editId = p?.id ?: ""
        name = p?.name ?: ""; url = p?.url ?: ""; worker = p?.worker ?: ""; pass = p?.password ?: ""; minPay = p?.minPayout ?: ""
        error = null
    }

    OverlayPage("Mining Pool", onBack) {
        GCard(Modifier.fillMaxWidth()) {
            MetricGrid(
                listOf(
                    "Connection" to st.pool.label(),
                    "Ping" to (if (st.pingMs >= 0) "${st.pingMs} ms" else "--"),
                    "Accepted shares" to "${st.accepted}",
                    "Rejected shares" to "${st.rejected}",
                )
            )
        }
        Label("Protocol: Stratum V1 over TCP or TLS. No pool is preconfigured: enter one you trust.")

        Repo.pools.forEach { p ->
            val selected = p.id == (Repo.activePool()?.id ?: "")
            GCard(Modifier.fillMaxWidth(), onClick = { Repo.selectPool(p.id) }) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Body(p.name.ifBlank { p.url }, weight = FontWeight.SemiBold)
                        Label(p.url)
                    }
                    if (selected) Body("Active", color = G.Accent, size = 13)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Body("Edit", color = G.Accent, size = 13, modifier = Modifier.clickable { open(p) })
                    Body("Delete", color = G.Warn, size = 13, modifier = Modifier.clickable { Repo.deletePool(p.id) })
                }
            }
        }

        if (editId == null) {
            GButton("ADD POOL", { open(null) }, Modifier.fillMaxWidth())
        } else {
            GCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GField(name, { name = it }, "Name (optional)")
                    GField(url, { url = it; error = null }, "Pool URL  (stratum+tcp://host:port)", keyboard = KeyboardType.Uri)
                    GField(worker, { worker = it }, "Worker Name  (e.g. address.worker)")
                    GField(pass, { pass = it }, "Password (if required)", password = true)
                    GField(minPay, { minPay = it }, "Minimum payout in BTC (from your pool, optional)", keyboard = KeyboardType.Decimal)
                    error?.let { Text(it, color = G.Warn, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GButton("Cancel", { editId = null }, Modifier.weight(1f), filled = false)
                        GButton("Save", {
                            if (parseEndpoint(url) == null) error = "Invalid URL. Use stratum+tcp://host:port or stratum+ssl://host:port"
                            else {
                                Repo.upsertPool(PoolConfig(editId!!.ifEmpty { UUID.randomUUID().toString() }, name.trim(), url.trim(), worker.trim(), pass, minPay.trim()))
                                editId = null
                            }
                        }, Modifier.weight(1f))
                    }
                }
            }
        }
        if (st.running) Label("Pool changes apply the next time you start mining.")
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val tf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    OverlayPage("Mining History", onBack) {
        if (Repo.sessions.isEmpty()) {
            GCard(Modifier.fillMaxWidth()) { Body("No sessions yet", color = G.Dim) }
        }
        Repo.sessions.reversed().forEach { s ->
            GCard(Modifier.fillMaxWidth()) {
                Body(df.format(Date(s.start)), weight = FontWeight.SemiBold)
                Label("${tf.format(Date(s.start))} – ${tf.format(Date(s.end))}")
                Spacer(Modifier.height(12.dp))
                MetricGrid(
                    listOf(
                        "Duration" to formatDuration((s.end - s.start) / 1000),
                        "Avg hashrate" to formatHashrate(s.avgHashrate),
                        "Shares" to "${s.accepted} ok · ${s.rejected} rej",
                        "Est. BTC" to formatBtc(s.estBtc),
                    )
                )
            }
        }
    }
}
