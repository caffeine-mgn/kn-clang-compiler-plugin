package pw.binom.kotlin.clang


import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.konan.clangTarget
import java.io.File

/**
 * Resolved Clang toolchain for a single Kotlin/Native target.
 *
 * Returned by [KnClangExtension.toolchain] and consumed by external build systems (CMake,
 * Meson, Bazel rules_cc, …) that need to know where the compiler, linker, archiver and
 * sysroot live, plus the [cFlags] the Kotlin/Native backend would have used.
 *
 * On Android, [compiler] / [cxxCompiler] are the per-target NDK wrappers
 * (`<ndk>/bin/<triple>-clang[++]`) and [archiver] / [linker] are still the host LLVM
 * `llvm-ar` / `lld` because the plugin links via the host toolchain.
 *
 * For all other families [compiler] / [cxxCompiler] are the host LLVM `clang/clang++`.
 *
 * [cFlags] already contains `-target <triple>`, `--sysroot=…`, `-B<ndk>/bin`,
 * `-isystem <llvm>/lib/clang/.../include`, `--gcc-toolchain=…` (Linux), `-fno-stack-protector`,
 * `-fexceptions` and the per-family defaults — pass them as `CMAKE_C_FLAGS_INIT` (or
 * equivalent) without modification.
 *
 * @property target          the Kotlin/Native target this toolchain was resolved for
 * @property triple          the Clang target triple (e.g. `aarch64-apple-darwin23.4.0`)
 * @property compiler        the C compiler (clang) — on Android: NDK `<triple>-clang`
 * @property cxxCompiler     the C++ compiler — same path as [compiler] with `++` appended
 * @property archiver        `llvm-ar` for static archives
 * @property linker          `lld` for shared-library linking (use only on non-Apple hosts;
 *                           Apple targets must use the default `ld64`)
 * @property sysRoot         target sysroot (Android: `arch-<arch>/android-21`;
 *                           Linux/MinGW: distro sysroot; Apple: Xcode SDK path)
 * @property toolchain       `--gcc-toolchain` value for Linux targets, `null` otherwise
 * @property cFlags          ready-to-use compile flags for [compiler]; includes
 *                           `-target`, `--sysroot`, `-B…`, `--gcc-toolchain` etc.
 * @property konanVersion    the Kotlin/Native version this toolchain was resolved from
 */
data class KnClangToolchain(
    val target: KonanTarget,
    val triple: String,
    val compiler: File,
    val cxxCompiler: File,
    val archiver: File,
    val linker: File,
    val sysRoot: File,
    val toolchain: File?,
    val cFlags: List<String>,
    val konanVersion: KonanVersionNumber,
) {
    companion object {
        /**
         * Resolves a toolchain by reading [BaseKonanVersion]'s cached metadata for
         * [version] and looking up [target]'s sysroot / LLVM dir / per-target flags.
         *
         * Throws if [target] is not supported by [version] (e.g. Apple targets on
         * non-macOS hosts — see [BaseKonanVersion.findTargetInfo]).
         */
        fun resolve(version: KonanVersionNumber, target: KonanTarget): KnClangToolchain {
            val kv = KonanVersion.getVersion(version)
            val info = kv.findTargetInfo(target)
                ?: error("Target ${target.name} is not supported by Kotlin/Native $version")
            val linker = kv.getLinked(target) as CLangLinker
            val clang = (kv.findCppCompiler(target) as CLang).clangFile

            val cxx = if (clang.name.endsWith(".exe")) {
                File(clang.parentFile, clang.nameWithoutExtension + "++" + ".exe")
            } else {
                File(clang.parentFile, clang.name + "++")
            }

            return KnClangToolchain(
                target = target,
                triple = target.clangTarget,
                compiler = clang,
                cxxCompiler = cxx,
                archiver = linker.arFile,
                linker = linker.ldFile,
                sysRoot = info.sysRoot.first(),
                toolchain = info.toolchain,
                cFlags = info.clangCompileArgs,
                konanVersion = version,
            )
        }
    }
}