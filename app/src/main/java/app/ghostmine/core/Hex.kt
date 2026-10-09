package app.ghostmine.core

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "odd hex length" }
    return ByteArray(length / 2) {
        ((Character.digit(this[2 * it], 16) shl 4) or Character.digit(this[2 * it + 1], 16)).toByte()
    }
}

private val HEX = "0123456789abcdef".toCharArray()

fun ByteArray.toHex(): String {
    val c = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xff
        c[2 * i] = HEX[v ushr 4]
        c[2 * i + 1] = HEX[v and 15]
    }
    return String(c)
}
