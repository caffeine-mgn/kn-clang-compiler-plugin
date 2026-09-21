package pw.binom.kotlin.clang

/**
 * Lightweight logger interface. Used by the toolchain core so the CLI can compile and run
 * without dragging in `gradleApi()`; the Gradle plugin adapts its own
 * `org.gradle.api.logging.Logger` to this type at the boundary.
 */
interface Logger {
    fun info(message: String)
    fun lifecycle(message: String)
    fun warn(message: String)
    fun error(message: String)
    fun debug(message: String)

    companion object {
        /** A logger that writes to stdout / stderr — used by the standalone CLI. */
        val Stdout: Logger = object : Logger {
            override fun info(message: String) = println(message)
            override fun lifecycle(message: String) = println(message)
            override fun warn(message: String) = println("WARNING: $message")
            override fun error(message: String) = System.err.println(message)
            override fun debug(message: String) { /* no-op */ }
        }
    }
}