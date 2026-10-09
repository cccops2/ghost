package app.ghostmine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class NavTab(val label: String) { Home("Home"), Mining("Mining"), Statistics("Statistics"), Wallet("Wallet") }
enum class Overlay { None, Settings, Pool, History }

@Composable
fun GhostApp() {
    var tab by remember { mutableStateOf(NavTab.Home) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    BackHandler(enabled = overlay != Overlay.None) {
        overlay = if (overlay == Overlay.Settings || overlay == Overlay.None) Overlay.None else Overlay.Settings
    }

    Surface(Modifier.fillMaxSize(), color = G.Bg, contentColor = Color.White) {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = overlay to tab,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "nav",
                    ) { (ov, t) ->
                        when (ov) {
                            Overlay.Settings -> SettingsScreen(
                                onBack = { overlay = Overlay.None },
                                onPool = { overlay = Overlay.Pool },
                                onHistory = { overlay = Overlay.History },
                            )
                            Overlay.Pool -> PoolScreen(onBack = { overlay = Overlay.Settings })
                            Overlay.History -> HistoryScreen(onBack = { overlay = Overlay.Settings })
                            Overlay.None -> when (t) {
                                NavTab.Home -> HomeScreen(
                                    onSettings = { overlay = Overlay.Settings },
                                    onWallet = { tab = NavTab.Wallet },
                                    onPool = { overlay = Overlay.Pool },
                                )
                                NavTab.Mining -> MiningScreen(onPool = { overlay = Overlay.Pool })
                                NavTab.Statistics -> StatsScreen(onHistory = { overlay = Overlay.History })
                                NavTab.Wallet -> WalletScreen()
                            }
                        }
                    }
                }
                if (overlay == Overlay.None) NavBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun NavBar(selected: NavTab, onSelect: (NavTab) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(G.Line))
        Row(Modifier.fillMaxWidth().height(60.dp)) {
            NavTab.values().forEach { t ->
                val sel = t == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(t.label, color = if (sel) Color.White else G.Dim, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (sel) G.Accent else Color.Transparent))
                }
            }
        }
    }
}
