package pw.binom.kotlin.clang

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.konan.jdkIncludePlatform
import java.io.File

/**
 * Resolved JDK include directories for a JVM JNI build.
 *
 * @property jdkInclude         directory containing `jni.h` (typically `<javaHome>/include`)
 * @property jdkIncludePlatform directory containing the per-architecture `jni_md.h`
 *                              (typically `<javaHome>/include/<platform>`, where
 *                              `<platform>` comes from [KonanTarget.jdkIncludePlatform])
 */
data class JdkIncludePaths(
    val jdkInclude: File,
    val jdkIncludePlatform: File,
) {
    /**
     * Both `jni.h` and `jni_md.h` are present at their expected paths.
     * Used to decide whether a cross-compiled JNI build can actually link.
     */
    fun isUsable(): Boolean =
        File(jdkInclude, "jni.h").isFile &&
            File(jdkIncludePlatform, "jni_md.h").isFile
}

/**
 * Locates the JDK include directory and the matching per-platform include
 * directory for a JNI build of a given [KonanTarget]. Used by
 * [pw.binom.kotlin.clang.clangBuildJni] to wire `-I` flags automatically.
 *
 * ### Lookup order
 *
 *  1. `JAVA_HOME` (if set and pointing at a directory that contains `include/`).
 *  2. Hard-coded fallbacks searched in order:
 *     - `/usr/lib/jvm/java-21-openjdk`
 *     - `/usr/lib/jvm/default-java`
 *
 * For the `jni_md.h` directory specifically, [resolve] consults
 * [overridePlatformDir] first (CI pre-staged headers) and only falls back
 * to the JDK's own `<javaHome>/include/<platform>/` if no override exists.
 */
object JdkIncludeResolver {
    private val fallbackJavaHomes: List<String> = listOf(
        "/usr/lib/jvm/java-21-openjdk",
        "/usr/lib/jvm/default-java",
    )

    /**
     * @param target Kotlin/Native target being built for (drives
     *               [KonanTarget.jdkIncludePlatform] for the per-arch headers).
     * @param overridePlatformDir if non-null, used as the `jni_md.h` directory
     *               when it contains `jni_md.h`. This is how the CI host
     *               (e.g. macOS "universal" runner building linux/mingw
     *               natives) gets headers that the local JDK does not ship.
     *               Pass `layout.buildDirectory.dir("jni-include")` of the
     *               calling Gradle project.
     * @throws IllegalStateException if no usable JAVA_HOME is found.
     */
    fun resolve(
        target: KonanTarget,
        overridePlatformDir: File? = null,
    ): JdkIncludePaths {
        val javaHome = javaHomeOrNull()
            ?: throw IllegalStateException(
                "Cannot locate a JDK for JNI build of $target. Set JAVA_HOME or install " +
                    "openjdk-21 at one of: ${fallbackJavaHomes.joinToString(", ")}",
            )
        val include = File(javaHome, "include")
        val platform = target.jdkIncludePlatform
        val platformDir = overridePlatformDir
            ?.takeIf { File(it, "jni_md.h").isFile }
            ?: File(include, platform)
        return JdkIncludePaths(
            jdkInclude = include,
            jdkIncludePlatform = platformDir,
        )
    }

    /**
     * Convenience: resolves include paths for the current Gradle host (used
     * by tools and tests that need the local JDK's headers regardless of
     * which target is being built).
     */
    val host: JdkIncludePaths
        get() = resolve(HostManager.host)

    private fun javaHomeOrNull(): String? {
        val env = System.getenv("JAVA_HOME")?.takeIf { File(it, "include").isDirectory }
        if (env != null) return env.also { return it }
        return fallbackJavaHomes.firstOrNull { File(it, "include").isDirectory }
    }
}
