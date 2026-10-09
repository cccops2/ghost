package app.ghostmine.stratum

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class PoolState { IDLE, CONNECTING, CONNECTED, ERROR }

data class Job(
    val id: String,
    val prevHash: String,
    val coinb1: String,
    val coinb2: String,
    val branches: List<String>,
    val version: String,
    val nbits: String,
    val ntime: String,
    val clean: Boolean,
)

data class Endpoint(val host: String, val port: Int, val tls: Boolean)

/** Accepts stratum+tcp://host:port, stratum+ssl://host:port (also tcp://, ssl://, tls://). */
fun parseEndpoint(raw: String): Endpoint? {
    val u = raw.trim()
    if (u.isEmpty()) return null
    val scheme = if (u.contains("://")) u.substringBefore("://").lowercase() else "stratum+tcp"
    val tls = scheme in setOf("stratum+ssl", "stratum+tls", "ssl", "tls")
    if (!tls && scheme !in setOf("stratum+tcp", "tcp")) return null
    val hp = u.substringAfter("://", u).trimEnd('/')
    val host = hp.substringBeforeLast(":", "")
    val port = hp.substringAfterLast(":", "").toIntOrNull()
    if (host.isBlank() || port == null || port !in 1..65535) return null
    return Endpoint(host, port, tls)
}

interface StratumListener {
    fun onState(state: PoolState, message: String)
    fun onSubscribed(extranonce1: String, extranonce2Size: Int)
    fun onDifficulty(difficulty: Double)
    fun onJob(job: Job)
    fun onShare(accepted: Boolean, reason: String?)
    fun onPing(ms: Long)
}

/** Minimal Stratum V1 client (subscribe / authorize / notify / set_difficulty / submit). No Android dependencies. */
class StratumClient(
    private val ep: Endpoint,
    private val user: String,
    private val pass: String,
    private val l: StratumListener,
) {
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    @Volatile private var out: Writer? = null
    @Volatile private var authorized = false
    private val ids = AtomicInteger(10)
    private val pending = ConcurrentHashMap<Int, Long>()
    private var thread: Thread? = null
    private var subSent = 0L

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "ghost-stratum").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        thread?.interrupt()
    }

    fun submit(jobId: String, extranonce2: String, ntime: String, nonce: String) {
        val id = ids.incrementAndGet()
        pending[id] = System.nanoTime()
        send(
            JSONObject().put("id", id).put("method", "mining.submit")
                .put("params", JSONArray().put(user).put(jobId).put(extranonce2).put(ntime).put(nonce))
        )
    }

    private fun send(o: JSONObject) {
        val w = out ?: return
        synchronized(w) {
            try {
                w.write(o.toString() + "\n")
                w.flush()
            } catch (e: IOException) {
                try { socket?.close() } catch (x: Exception) { }
            }
        }
    }

    private fun loop() {
        var backoff = 2000L
        while (running) {
            try {
                l.onState(PoolState.CONNECTING, "")
                session()
            } catch (e: Exception) {
                if (!running) break
                l.onState(PoolState.ERROR, e.message ?: e.javaClass.simpleName)
            } finally {
                out = null
                try { socket?.close() } catch (e: Exception) { }
                pending.clear()
            }
            if (!running) break
            if (authorized) { backoff = 2000L; authorized = false }
            try { Thread.sleep(backoff) } catch (e: InterruptedException) { break }
            backoff = minOf(backoff * 2, 30000L)
        }
        l.onState(PoolState.IDLE, "")
    }

    private fun session() {
        val plain = Socket()
        socket = plain
        plain.connect(InetSocketAddress(ep.host, ep.port), 15000)
        val s: Socket = if (ep.tls) {
            val ss = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plain, ep.host, ep.port, true) as SSLSocket
            val p = ss.sslParameters
            p.endpointIdentificationAlgorithm = "HTTPS" // verify the pool certificate hostname
            ss.sslParameters = p
            ss.startHandshake()
            ss
        } else plain
        s.soTimeout = 300_000
        socket = s
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        out = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        subSent = System.nanoTime()
        send(JSONObject().put("id", 1).put("method", "mining.subscribe").put("params", JSONArray().put("GhostMine/1.0")))
        while (running) {
            val line = reader.readLine() ?: throw IOException("Pool closed the connection")
            if (line.isBlank()) continue
            handle(line)
        }
    }

    private fun handle(line: String) {
        val j = try { JSONObject(line) } catch (e: JSONException) { return }
        val method = j.optString("method", "")
        if (method.isNotEmpty()) {
            val p = j.optJSONArray("params") ?: return
            when (method) {
                "mining.notify" -> {
                    val br = p.getJSONArray(4)
                    val list = ArrayList<String>(br.length())
                    for (i in 0 until br.length()) list.add(br.getString(i))
                    l.onJob(
                        Job(
                            p.getString(0), p.getString(1), p.getString(2), p.getString(3), list,
                            p.getString(5), p.getString(6), p.getString(7), p.optBoolean(8, false)
                        )
                    )
                }
                "mining.set_difficulty" -> l.onDifficulty(p.getDouble(0))
            }
            return
        }
        val id = j.optInt("id", -1)
        val errMsg: String? =
            if (j.isNull("error")) null else (j.optJSONArray("error")?.optString(1) ?: j.opt("error").toString())
        when (id) {
            1 -> {
                if (errMsg != null) throw IOException("Subscribe failed: $errMsg")
                val res = j.getJSONArray("result")
                l.onPing((System.nanoTime() - subSent) / 1_000_000)
                l.onSubscribed(res.getString(1), res.getInt(2))
                send(
                    JSONObject().put("id", 2).put("method", "mining.authorize")
                        .put("params", JSONArray().put(user).put(pass))
                )
            }
            2 -> {
                if (j.optBoolean("result", false)) {
                    authorized = true
                    l.onState(PoolState.CONNECTED, "")
                } else throw IOException("Authorization rejected by pool")
            }
            else -> {
                val t = pending.remove(id) ?: return
                l.onPing((System.nanoTime() - t) / 1_000_000)
                l.onShare(j.optBoolean("result", false) && errMsg == null, errMsg)
            }
        }
    }
}
