package pw.binom.kotlin.clang

/**
 * Minimal semver-compatible version number. Drop-in replacement for
 * `org.gradle.util.internal.KonanVersionNumber` (which is internal Gradle API and not on
 * Maven Central) for the toolchain core.
 *
 * Use this everywhere — both the JVM Gradle plugin and the standalone CLI rely on it.
 */
data class KonanVersionNumber(val raw: String) : Comparable<KonanVersionNumber> {
    private val parts: IntArray by lazy {
        raw.split('.').map { it.toIntOrNull() ?: 0 }.toIntArray()
    }

    override fun compareTo(other: KonanVersionNumber): Int {
        val max = maxOf(parts.size, other.parts.size)
        for (i in 0 until max) {
            val a = parts.getOrElse(i) { 0 }
            val b = other.parts.getOrElse(i) { 0 }
            if (a != b) return a - b
        }
        return 0
    }

    override fun toString(): String = raw

    companion object {
        fun parse(raw: String): KonanVersionNumber = KonanVersionNumber(raw)
    }
}