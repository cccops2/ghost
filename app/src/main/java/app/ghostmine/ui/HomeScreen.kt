package app.ghostmine.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ghostmine.core.Mode
import app.ghostmine.core.formatBtcPlain
import app.ghostmine.core.formatDuration
import app.ghostmine.core.formatTemp
import app.ghostmine.core.hashrateParts
import app.ghostmine.core.shortAddress
import app.ghostmine.data.Repo
import app.ghostmine.hardware.HardwareMonitor
import app.ghostmine.miner.MinerController
import app.ghostmine.stratum.PoolState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onSettings: () -> Unit, onWallet: () -> Unit, onPool: () -> Unit) {
    val ctx = LocalContext.current
    val st by MinerController.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idleTemp by remember { mutableStateOf(Float.NaN) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(st.running) { while (st.running) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(Unit) { while (true) { idleTemp = HardwareMonitor.live(ctx).tempC; delay(5000) } }

    val hr by animateFloatAsState(st.hashrate.toFloat(), tween(400), label = "hr")
    val (num, unit) = hashrateParts(hr.toDouble())
    val temp = if (st.running) st.tempC else idleTemp
    val uptime = if (st.running) (now - st.startedAt) / 1000 else 0L

    fun begin() {
        val e = MinerController.start()
        if (e != null) error = e else Toast.makeText(ctx, "Mining started", Toast.LENGTH_SHORT).show()
    }

    val needsWarning = !Repo.warned || Repo.mode == Mode.PERFORMANCE ||
        (Repo.mode == Mode.CUSTOM && Repo.custom.cpuLimit >= 70)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostLogo(26.dp, active = st.running)
            Spacer(Modifier.width(10.dp))
            Text("GHOST MINE", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = G.Dim) }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(st.running && st.pool == PoolState.CONNECTED)
                    Spacer(Modifier.width(8.dp))
                    Label("Mining")
                    Spacer(Modifier.width(6.dp))
                    val s = when {
                        !st.running -> "Idle"
                        st.pool == PoolState.CONNECTED -> "Active"
                        else -> "Connecting"
                    }
                    Text(s, color = if (st.running) G.Accent else G.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(num, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(unit, color = G.Dim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Label("Current Hashrate")
                Spacer(Modifier.height(32.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Est. BTC", formatBtcPlain(st.estBtc), Modifier.weight(1f))
                    StatCard("Uptime", formatDuration(uptime), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Temperature", formatTemp(temp), Modifier.weight(1f))
                    StatCard("Shares", "${st.accepted} accepted", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.clickable { onWallet() }.padding(8.dp)) {
                    Label("Payout  ")
                    Label(shortAddress(Repo.address), color = if (Repo.address.isBlank()) G.Warn else Color.White)
                }
            }
        }

        GButton(
            if (st.running) "STOP MINING" else "START MINING",
            onClick = {
                if (st.running) {
                    MinerController.stop()
                    Toast.makeText(ctx, "Mining stopped", Toast.LENGTH_SHORT).show()
                } else if (needsWarning) confirm = true else begin()
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 8.dp),
            filled = !st.running,
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = G.Card, titleContentColor = Color.White, textContentColor = G.Dim,
            title = { Text("Before you start") },
            text = {
                Text(
                    "Phones produce a tiny fraction of the hash power needed to earn Bitcoin, so expect " +
                        "close to zero BTC. Mining uses real battery and generates heat. Ghost Mine lowers " +
                        "intensity automatically when your device gets warm."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false; Repo.setWarned(); begin() }) { Text("Start", color = G.Accent) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = G.Dim) } },
        )
    }
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            containerColor = G.Card, titleContentColor = Color.White, textContentColor = G.Dim,
            title = { Text("Can't start yet") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { error = null; if (msg.contains("pool", true)) onPool() else onWallet() }) {
                    Text(if (msg.contains("pool", true)) "Open Mining Pool" else "Open Wallet", color = G.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { error = null }) { Text("Close", color = G.Dim) } },
        )
    }
}
