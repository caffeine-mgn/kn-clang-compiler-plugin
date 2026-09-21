package pw.binom.kotlin.clang

/**
 * Adapter from Gradle's `org.gradle.api.logging.Logger` to the toolchain core's
 * `Logger` interface, so the JVM plugin can hand its logger to the core without
 * pulling `gradleApi` into the core's compile classpath.
 */
internal fun org.gradle.api.logging.Logger.toKnLogger(): Logger =
    object : Logger {
        override fun info(message: String) = this@toKnLogger.info(message)
        override fun lifecycle(message: String) = this@toKnLogger.lifecycle(message)
        override fun warn(message: String) = this@toKnLogger.warn(message)
        override fun error(message: String) = this@toKnLogger.error(message)
        override fun debug(message: String) = this@toKnLogger.debug(message)
    }