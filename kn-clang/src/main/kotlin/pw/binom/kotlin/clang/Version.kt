package pw.binom.kotlin.clang

@JvmInline
value class Version(val raw: String) {
    operator fun compareTo(other: Version): Int {
        val currentVersion = raw.split('.')
        val otherVersion = other.raw.split('.')
        val maxLen = maxOf(currentVersion.size, otherVersion.size)
        for (i in 0 until maxLen) {
            val c = currentVersion.getOrNull(i)?.toInt() ?: 0
            val o = otherVersion.getOrNull(i)?.toInt() ?: 0
            val value = c - o
            if (value == 0) {
                continue
            }
            return value
        }
        return 0
    }

    override fun toString(): String = raw
}