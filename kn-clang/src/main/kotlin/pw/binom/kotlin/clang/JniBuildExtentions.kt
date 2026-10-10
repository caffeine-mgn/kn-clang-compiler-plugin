package pw.binom.kotlin.clang

import org.gradle.api.Project
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName
import pw.binom.kotlin.clang.konan.jdkIncludePlatform
import java.io.File

/**
 * One-shot JVM JNI build: compile and link [name] as a shared library for
 * every target in [targets], automatically adding the JDK's `jni.h` and
 * `jni_md.h` to the include path.
 *
 * This is the canonical entry point for shipping a JNI native library next
 * to a JVM jar — see `klua/build.gradle.kts` for a real-world usage example.
 *
 * ### What it does
 *
 * - Registers one `clangBuildDynamic` task per target with
 *   `useLibPrefix = true`, so each output is named `lib<name>.<ext>`
 *   (`libklua.so` / `libklua.dylib` / `libklua.dll`) — the JNI convention.
 *   Output paths land under `build/native/<name>/<target>/dynamic/`.
 * - Calls [JdkIncludeResolver] for each target and adds the resolved
 *   `jni.h` + `jni_md.h` directories to the include path. The directories
 *   are added even if they are missing on disk; the per-target `onlyIf`
 *   below makes sure the build doesn't actually try to compile against
 *   absent headers.
 * - Adds an `onlyIf` guard so cross-targets whose JDK headers are not
 *   available (and have not been pre-staged via [jniHeadersOverride]) are
 *   quietly skipped at execution time instead of failing.
 *
 * ### Cross-target headers
 *
 * A host JDK only ships JNI headers for its own ABI family, so compiling
 * `clangBuildDynamic(target = MINGW_X64)` on a Linux runner without help
 * would have no `jni_md.h` to include. CI solves this by pre-staging a
 * directory such as `build/jni-include/win32/jni_md.h` (typically copied
 * from OpenJDK's `src/java.base/share/native/...` or
 * `openjdk-jni-headers`), and pointing [jniHeadersOverride] at it.
 *
 * @param name C library name; used as both task suffix and the output
 *             filename prefix (`lib<name>.<ext>`).
 * @param targets Native targets to build for. Typically `LINUX_X64`,
 *                `LINUX_ARM64`, `MINGW_X64`, plus the current host when it
 *                is macOS (`HOST`).
 * @param jniHeadersOverride Optional directory whose `<platform>/jni_md.h`
 *                subdirectories carry CI-pre-staged headers for cross-
 *                targets. Pass
 *                `layout.buildDirectory.dir("jni-include")` from a Gradle
 *                project that provisions those files in CI.
 * @param configure Block applied to every per-target `BuildDynamicTask`.
 *                  Use it to set `compileArgs`, add `compileDir`s, override
 *                  `konanVersion`, etc.
 * @return the registered per-target tasks, in the same order as [targets].
 */
fun Project.clangBuildJni(
    name: String,
    targets: Iterable<KonanTarget>,
    jniHeadersOverride: File? = null,
    configure: BuildDynamicTask.() -> Unit = {},
): List<BuildDynamicTask> {
    val host = HostManager.host
    return targets.map { target ->
        val effectiveOverride = jniHeadersOverride
            ?.let { File(it, target.jdkIncludePlatform) }
        val jdk = JdkIncludeResolver.resolve(target, effectiveOverride)
        val isHost = target == host
        val jniUsable = jdk.isUsable()
        val taskName = "buildJni" +
            name.replaceFirstChar { c -> c.uppercase() } +
            target.presetName.replaceFirstChar { c -> c.uppercase() }
        clangBuildDynamic(
            target = target,
            name = name,
            taskName = taskName,
            useLibPrefix = true,
        ) {
            // Add includes if they physically exist on the host so the C
            // source can `#include <jni.h>` / `<jni_md.h>`. clang ignores
            // `-I` paths that don't exist; the onlyIf below guards the
            // actual compile step.
            if (jdk.jdkInclude.isDirectory) include(jdk.jdkInclude)
            if (jdk.jdkIncludePlatform.isDirectory) include(jdk.jdkIncludePlatform)

            configure()

            // Skip cross-targets whose JDK headers aren't present and
            // weren't staged by CI. Building the host target always works.
            onlyIf("$name JNI headers for $target") {
                isHost || jniUsable
            }
        }
    }
}

/**
 * Adds the JNI C bridge source directory to a [BuildDynamicTask].
 *
 * Defaults to the KMP-conventional `src/jvmMain/c` path. Pass [dir] when
 * the project keeps its JNI bridge in a non-standard location.
 *
 * Example:
 * ```
 * clangBuildJni(name = "klua", targets = jvmHostTargets) {
 *     include(LUA_SOURCES_DIR)
 *     compileDirJni()                  // picks up src/jvmMain/c
 *     compileArgs("-std=gnu99", "-DLUA_COMPAT_5_3")
 * }
 * ```
 */
fun BuildDynamicTask.compileDirJni(dir: File? = null) {
    val sourceDir = dir
        ?: project.layout.projectDirectory.dir("src/jvmMain/c").asFile
    compileDir(sourceDir = sourceDir)
}
