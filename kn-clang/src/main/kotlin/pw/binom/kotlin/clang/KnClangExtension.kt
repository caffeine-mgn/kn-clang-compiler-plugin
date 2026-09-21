package pw.binom.kotlin.clang

import org.gradle.api.provider.Property
import pw.binom.kotlin.clang.KonanVersionNumber
import org.jetbrains.kotlin.konan.target.KonanTarget

abstract class KnClangExtension {
    abstract val konanVersion: Property<String>

    init {
        konanVersion.convention(KotlinVersions.V2_4_20.toString())
    }

    /**
     * Resolves the Clang toolchain for [target] (compiler, linker, archiver, sysroot,
     * `-target`/sysroot/toolchain flags) from the Kotlin/Native distribution selected by
     * [konanVersion].
     *
     * On the first call the target's sysroot is fetched on demand (via
     * [Konan.checkSysrootInstalled]); subsequent calls are cheap. The returned
     * [KnClangToolchain] is a plain data object — read it at task execution time, not at
     * configuration time, to keep Gradle configuration fast and lazy.
     *
     * Designed for handing the toolchain off to an external build system (CMake, Meson,
     * Bazel, …). See [building-with-external-tools.md](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/blob/main/building-with-external-tools.md)
     * for the CMake recipe.
     */
    fun toolchain(target: KonanTarget): KnClangToolchain {
        val version = KonanVersionNumber.parse(konanVersion.get())
        Konan.checkSysrootInstalled(version, target)
        return KnClangToolchain.resolve(version, target)
    }
}