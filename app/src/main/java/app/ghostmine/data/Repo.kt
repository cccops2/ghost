package app.ghostmine.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.ghostmine.core.CustomSettings
import app.ghostmine.core.Mode
import app.ghostmine.core.PoolConfig
import app.ghostmine.core.Sample
import app.ghostmine.core.Session
import org.json.JSONArray
import org.json.JSONObject

/** Settings + history. Pool credentials and the public BTC address live in EncryptedSharedPreferences. */
object Repo {
    private lateinit var secure: SharedPreferences
    private lateinit var plain: SharedPreferences

    var address by mutableStateOf(""); private set
    var mode by mutableStateOf(Mode.BALANCED); private set
    var custom by mutableStateOf(CustomSettings(2, 50, 41, false)); private set
    var pools by mutableStateOf(listOf<PoolConfig>()); private set
    var selectedPool by mutableStateOf(""); private set
    var sessions by mutableStateOf(listOf<Session>()); private set
    var samples by mutableStateOf(listOf<Sample>()); private set
    var notifOn by mutableStateOf(true); private set
    var stopLowBattery by mutableStateOf(true); private set
    var warned by mutableStateOf(false); private set

    private fun createSecure(ctx: Context): SharedPreferences {
        fun build(): SharedPreferences {
            val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            return EncryptedSharedPreferences.create(
                ctx, "ghost_secure", mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try { build() } catch (e: Exception) {
            ctx.deleteSharedPreferences("ghost_secure")
            build()
        }
    }

    fun init(ctx: Context, defaultCustom: CustomSettings) {
        secure = createSecure(ctx)
        plain = ctx.getSharedPreferences("ghost_plain", Context.MODE_PRIVATE)
        address = secure.getString("addr", "") ?: ""
        selectedPool = secure.getString("pool_sel", "") ?: ""
        pools = try {
            val a = JSONArray(secure.getString("pools", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                PoolConfig(o.getString("id"), o.optString("name"), o.getString("url"), o.optString("worker"),
                    o.optString("password"), o.optString("minPayout"))
            }
        } catch (e: Exception) { emptyList() }
        mode = try { Mode.valueOf(plain.getString("mode", "BALANCED")!!) } catch (e: Exception) { Mode.BALANCED }
        custom = CustomSettings(
            plain.getInt("c_threads", defaultCustom.threads), plain.getInt("c_cpu", defaultCustom.cpuLimit),
            plain.getInt("c_temp", defaultCustom.maxTemp), plain.getBoolean("c_charging", false)
        )
        notifOn = plain.getBoolean("notif", true)
        stopLowBattery = plain.getBoolean("lowbat", true)
        warned = plain.getBoolean("warned", false)
        sessions = try {
            val a = JSONArray(plain.getString("sessions", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Session(o.getLong("s"), o.getLong("e"), o.getDouble("h"), o.getInt("a"), o.getInt("r"), o.getDouble("b"))
            }
        } catch (e: Exception) { emptyList() }
        samples = try {
            val a = JSONArray(plain.getString("samples", "[]"))
            (0 until a.length()).map { val o = a.getJSONArray(it); Sample(o.getLong(0), o.getDouble(1)) }
        } catch (e: Exception) { emptyList() }
    }

    fun activePool(): PoolConfig? = pools.firstOrNull { it.id == selectedPool } ?: pools.firstOrNull()

    fun setAddress(a: String) { address = a; secure.edit().putString("addr", a).apply() }

    fun upsertPool(p: PoolConfig) {
        pools = if (pools.any { it.id == p.id }) pools.map { if (it.id == p.id) p else it } else pools + p
        persistPools()
        selectPool(p.id)
    }

    fun deletePool(id: String) {
        pools = pools.filterNot { it.id == id }
        if (selectedPool == id) selectedPool = pools.firstOrNull()?.id ?: ""
        persistPools()
        secure.edit().putString("pool_sel", selectedPool).apply()
    }

    fun selectPool(id: String) { selectedPool = id; secure.edit().putString("pool_sel", id).apply() }

    private fun persistPools() {
        val a = JSONArray()
        pools.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("worker", it.worker)
                .put("password", it.password).put("minPayout", it.minPayout))
        }
        secure.edit().putString("pools", a.toString()).apply()
    }

    fun setMode(m: Mode) { mode = m; plain.edit().putString("mode", m.name).apply() }

    fun setCustom(c: CustomSettings) {
        custom = c
        plain.edit().putInt("c_threads", c.threads).putInt("c_cpu", c.cpuLimit).putInt("c_temp", c.maxTemp)
            .putBoolean("c_charging", c.onlyCharging).apply()
    }

    fun setNotif(v: Boolean) { notifOn = v; plain.edit().putBoolean("notif", v).apply() }
    fun setStopLowBattery(v: Boolean) { stopLowBattery = v; plain.edit().putBoolean("lowbat", v).apply() }
    fun setWarned() { warned = true; plain.edit().putBoolean("warned", true).apply() }

    fun addSession(s: Session) {
        sessions = (sessions + s).takeLast(500)
        val a = JSONArray()
        sessions.forEach {
            a.put(JSONObject().put("s", it.start).put("e", it.end).put("h", it.avgHashrate)
                .put("a", it.accepted).put("r", it.rejected).put("b", it.estBtc))
        }
        plain.edit().putString("sessions", a.toString()).apply()
    }

    fun addSample(s: Sample) {
        samples = (samples + s).takeLast(9000)
        val a = JSONArray()
        samples.forEach { a.put(JSONArray().put(it.ts).put(it.hashrate)) }
        plain.edit().putString("samples", a.toString()).apply()
    }
}
