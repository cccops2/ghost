package app.ghostmine.core

enum class Mode { ECO, BALANCED, PERFORMANCE, CUSTOM }

data class PoolConfig(
    val id: String,
    val name: String,
    val url: String,
    val worker: String,
    val password: String,
    val minPayout: String,
)

data class CustomSettings(val threads: Int, val cpuLimit: Int, val maxTemp: Int, val onlyCharging: Boolean)

data class Session(
    val start: Long,
    val end: Long,
    val avgHashrate: Double,
    val accepted: Int,
    val rejected: Int,
    val estBtc: Double,
)

data class Sample(val ts: Long, val hashrate: Double)
