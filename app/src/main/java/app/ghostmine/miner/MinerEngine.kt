package app.ghostmine.miner

import app.ghostmine.core.hexToBytes
import app.ghostmine.core.toHex
import app.ghostmine.stratum.Job
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

private val DIFF1 = BigInteger("00000000ffff0000000000000000000000000000000000000000000000000000", 16)
private val MAX256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
private const val BATCH = 4096
private const val MAX_NONCE = 0xFFFFFFFFL

/** Network difficulty from the compact "nbits" field (real data from the pool's job). */
fun difficultyFromNbits(hex: String): Double {
    val n = hex.toLongOrNull(16) ?: return 0.0
    val exp = (n shr 24).toInt()
    val mant = (n and 0xffffff).toDouble()
    if (mant == 0.0) return 0.0
    return (65535.0 / mant) * Math.pow(256.0, (0x1d - exp).toDouble())
}

private fun targetFor(d: Double): ByteArray {
    var t = BigDecimal(DIFF1).divide(BigDecimal(d), 0, RoundingMode.DOWN).toBigInteger()
    if (t > MAX256) t = MAX256
    val b = t.toByteArray()
    val src = if (b.size > 32) b.copyOfRange(b.size - 32, b.size) else b
    val out = ByteArray(32)
    System.arraycopy(src, 0, out, 32 - src.size, src.size)
    return out
}

/**
 * Real CPU SHA-256d miner. Pure JVM (no Android classes) so it can be reused by a desktop build.
 * Every hash is computed here; nothing is simulated.
 */
class MinerEngine(private val submit: (jobId: String, extranonce2: String, ntime: String, nonce: String) -> Unit) {

    private class Work(val job: Job, val en1: ByteArray, val en2Size: Int, val target: ByteArray, val gen: Long)

    val totalHashes = AtomicLong(0)
    @Volatile var cpuLimit = 50
    @Volatile private var work: Work? = null
    @Volatile private var epoch = 0
    private var workers: List<Thread> = emptyList()
    private var en1: ByteArray? = null
    private var en2Size = 4
    private var difficulty = 1.0
    private var job: Job? = null
    private val gen = AtomicLong()

    @Synchronized fun setSubscription(en1Hex: String, size: Int) { en1 = en1Hex.hexToBytes(); en2Size = size; rebuild() }
    @Synchronized fun setDifficulty(d: Double) { if (d > 0) { difficulty = d; rebuild() } }
    @Synchronized fun setJob(j: Job) { job = j; rebuild() }

    private fun rebuild() {
        val j = job ?: return
        val e = en1 ?: return
        work = Work(j, e, en2Size, targetFor(difficulty), gen.incrementAndGet())
    }

    @Synchronized fun start(threads: Int) {
        epoch++
        val ep = epoch
        workers.forEach { it.interrupt() }
        workers = (0 until threads).map { i ->
            Thread({ worker(i, threads, ep) }, "ghost-miner-$i").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
                start()
            }
        }
    }

    @Synchronized fun stop() {
        epoch++
        workers.forEach { it.interrupt() }
        workers = emptyList()
    }

    private fun worker(idx: Int, count: Int, ep: Int) {
        val md = MessageDigest.getInstance("SHA-256")
        var lastGen = -1L
        var cur: Work? = null
        var header = ByteArray(80)
        var en2Hex = ""
        var en2Counter = 0L
        var nonce = 0L
        try {
            while (epoch == ep) {
                val w = work
                if (w == null) { Thread.sleep(100); continue }
                if (w.gen != lastGen) {
                    lastGen = w.gen
                    cur = w
                    en2Counter = idx.toLong()
                    nonce = 0
                    val p = buildHeader(w, en2Counter, md)
                    header = p.first
                    en2Hex = p.second
                }
                val c = cur ?: continue
                val t0 = System.nanoTime()
                var n = 0
                while (n < BATCH && nonce <= MAX_NONCE) {
                    header[76] = nonce.toByte()
                    header[77] = (nonce shr 8).toByte()
                    header[78] = (nonce shr 16).toByte()
                    header[79] = (nonce shr 24).toByte()
                    val h = md.digest(md.digest(header))
                    if (meets(h, c.target)) {
                        submit(c.job.id, en2Hex, c.job.ntime, header.copyOfRange(76, 80).toHex())
                    }
                    nonce++
                    n++
                }
                totalHashes.addAndGet(n.toLong())
                if (nonce > MAX_NONCE) {
                    en2Counter += count
                    nonce = 0
                    val p = buildHeader(c, en2Counter, md)
                    header = p.first
                    en2Hex = p.second
                }
                val lim = cpuLimit.coerceIn(5, 100)
                if (lim < 100) {
                    val busy = System.nanoTime() - t0
                    val sleepNs = busy * (100 - lim) / lim
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                }
            }
        } catch (e: InterruptedException) {
            // worker replaced or engine stopped
        }
    }

    /** hash (internal byte order) <= target (big-endian)? */
    private fun meets(h: ByteArray, t: ByteArray): Boolean {
        for (i in 0 until 32) {
            val a = h[31 - i].toInt() and 0xff
            val b = t[i].toInt() and 0xff
            if (a < b) return true
            if (a > b) return false
        }
        return true
    }

    private fun dsha(md: MessageDigest, b: ByteArray): ByteArray = md.digest(md.digest(b))

    private fun buildHeader(w: Work, en2c: Long, md: MessageDigest): Pair<ByteArray, String> {
        val en2 = ByteArray(w.en2Size)
        var v = en2c
        for (i in w.en2Size - 1 downTo 0) { en2[i] = v.toByte(); v = v ushr 8 }
        val j = w.job
        val coinbase = j.coinb1.hexToBytes() + w.en1 + en2 + j.coinb2.hexToBytes()
        var root = dsha(md, coinbase)
        for (b in j.branches) root = dsha(md, root + b.hexToBytes())
        val h = ByteArray(80)
        j.version.hexToBytes().reversedArray().copyInto(h, 0)
        val prev = j.prevHash.hexToBytes()
        for (i in 0 until 8) for (k in 0 until 4) h[4 + 4 * i + k] = prev[4 * i + 3 - k]
        root.copyInto(h, 36)
        j.ntime.hexToBytes().reversedArray().copyInto(h, 68)
        j.nbits.hexToBytes().reversedArray().copyInto(h, 72)
        return h to en2.toHex()
    }
}
