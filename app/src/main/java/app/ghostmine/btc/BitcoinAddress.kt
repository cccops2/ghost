package app.ghostmine.btc

import java.math.BigInteger
import java.security.MessageDigest

/** Mainnet Bitcoin address validation: Base58Check (P2PKH/P2SH), Bech32 (v0) and Bech32m (v1+). Pure Kotlin. */
object BitcoinAddress {
    fun isValid(address: String): Boolean {
        val s = address.trim()
        return isBase58(s) || isSegwit(s)
    }

    private const val B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val CS = "qpzry9x8gf2tvdw0s3jn54khe6mua7l"

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    private fun isBase58(s: String): Boolean {
        if (s.length !in 26..35 || !(s[0] == '1' || s[0] == '3')) return false
        var n = BigInteger.ZERO
        val k = BigInteger.valueOf(58)
        for (c in s) {
            val i = B58.indexOf(c)
            if (i < 0) return false
            n = n.multiply(k).add(BigInteger.valueOf(i.toLong()))
        }
        var raw = n.toByteArray()
        if (raw.size > 1 && raw[0] == 0.toByte()) raw = raw.copyOfRange(1, raw.size)
        val zeros = s.takeWhile { it == '1' }.length
        val full = ByteArray(zeros) + raw
        if (full.size != 25) return false
        val ver = full[0].toInt() and 0xff
        if (ver != 0 && ver != 5) return false
        val h = sha256(sha256(full.copyOfRange(0, 21)))
        for (i in 0..3) if (h[i] != full[21 + i]) return false
        return true
    }

    private fun polymod(v: IntArray): Int {
        val g = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (x in v) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor x
            for (i in 0..4) if (((b ushr i) and 1) == 1) chk = chk xor g[i]
        }
        return chk
    }

    private fun convert5to8(d: IntArray): ByteArray? {
        var acc = 0
        var bits = 0
        val out = ArrayList<Byte>()
        for (v in d) {
            acc = (acc shl 5) or v
            bits += 5
            while (bits >= 8) {
                bits -= 8
                out.add(((acc shr bits) and 0xff).toByte())
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (bits >= 5 || acc != 0) return null
        return out.toByteArray()
    }

    private fun isSegwit(s: String): Boolean {
        if (s.length !in 14..74) return false
        if (s != s.lowercase() && s != s.uppercase()) return false
        val a = s.lowercase()
        if (!a.startsWith("bc1")) return false
        val data = a.substring(3)
        val vals = IntArray(data.length)
        for (i in data.indices) {
            val x = CS.indexOf(data[i])
            if (x < 0) return false
            vals[i] = x
        }
        if (vals.size < 7) return false
        val hrpExpanded = intArrayOf(3, 3, 0, 2, 3) // "bc"
        val chk = polymod(hrpExpanded + vals)
        val ver = vals[0]
        val prog = convert5to8(vals.copyOfRange(1, vals.size - 6)) ?: return false
        return when (ver) {
            0 -> chk == 1 && (prog.size == 20 || prog.size == 32)
            in 1..16 -> chk == 0x2bc830a3 && prog.size in 2..40 && (ver != 1 || prog.size == 32)
            else -> false
        }
    }
}
