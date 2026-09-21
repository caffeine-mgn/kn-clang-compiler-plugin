package pw.binom.kotlin.clang.konan


import org.jetbrains.kotlin.konan.target.Family
import pw.binom.kotlin.clang.KonanVersionNumber
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.Xcode
import pw.binom.kotlin.clang.CLang
import pw.binom.kotlin.clang.CLangLinker
import pw.binom.kotlin.clang.CppCompiler
import pw.binom.kotlin.clang.KONAN_DEPS
import pw.binom.kotlin.clang.KONAN_USER_DIR
import pw.binom.kotlin.clang.KonanVersion
import pw.binom.kotlin.clang.Linker
import pw.binom.kotlin.clang.PREBUILD_KONAN_DIR_NAME
import pw.binom.kotlin.clang.TargetInfo
import java.io.File
import java.util.Properties

class BaseKonanVersion(val kotlinVersion: KonanVersionNumber) : KonanVersion {

    companion object {
        val exe = if (HostManager.hostIsMingw) ".exe" else ""
        const val ANDROID_API_LEVEL = 21
        private val arch = System.getProperty("os.arch")

        val HOST_TARGET: KonanTarget = when {
            HostManager.hostIsLinux -> KonanTarget.LINUX_X64
            HostManager.hostIsMac && arch == "aarch64" -> KonanTarget.MACOS_ARM64
            HostManager.hostIsMac -> KonanTarget.MACOS_X64
            HostManager.hostIsMingw -> KonanTarget.MINGW_X64
            else -> error("Unknown host OS")
        }
    }

    private val konanPropsFile: File = KONAN_USER_DIR
        .resolve(PREBUILD_KONAN_DIR_NAME(kotlinVersion))
        .resolve("konan/konan.properties")

    private val props: Properties by lazy {
        if (!konanPropsFile.exists()) {
            throw RuntimeException(
                "Cannot find $konanPropsFile. Was Kotlin/Native $kotlinVersion downloaded? " +
                    "Run `./gradlew downloadKonan` first."
            )
        }
        Properties().apply { load(konanPropsFile.inputStream()) }
    }

    private fun prop(key: String): String? = props.getProperty(key)

    private fun requiredProp(key: String): String =
        prop(key) ?: throw RuntimeException("'$key' not found in $konanPropsFile")

    private val HOST_KONAN_LLVM_DIR_NAME: String =
        requiredProp("llvm.${HOST_TARGET.name}.user")

    private val LLVM_VERSION: String =
        requiredProp("llvmVersion.${HOST_TARGET.name}")

    val LLVM_INCLUDE_DIR =
        "$KONAN_DEPS/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include"
    val LLVM_BIN = KONAN_DEPS.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/clang$exe")
    val LD_BIN = KONAN_DEPS.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/lld$exe")
    val LLVM_AR_BIN_FOLDER = KONAN_DEPS.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/llvm-ar$exe")
    override val HOST_LLVM_BIN_FOLDER = KONAN_DEPS.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin")

    private val optimizeLevel = 2

    private fun resolveKonanPath(raw: String): File {
        var current = raw
        var depth = 0
        while (current.startsWith("$")) {
            if (depth++ > 20) {
                throw RuntimeException("Too deep property resolution for \"$raw\" in $konanPropsFile")
            }
            val body = current.substring(1)
            val slash = body.indexOf('/')
            val varName = if (slash < 0) body else body.substring(0, slash)
            val rest = if (slash < 0) "" else body.substring(slash + 1)
            val resolved = requiredProp(varName)
            current = if (rest.isEmpty()) resolved else "$resolved/$rest"
        }
        return KONAN_DEPS.resolve(current)
    }

    private fun toolchainDir(target: KonanTarget): File? =
        prop("toolchainDependency.${target.name}")
            ?.let { KONAN_DEPS.resolve(it) }

    private fun androidNdkDir(target: KonanTarget): File {
        val key = "targetToolchain.${HOST_TARGET.name}-${target.name}"
        return KONAN_DEPS.resolve(requiredProp(key))
    }

    private fun androidNdkTriple(target: KonanTarget): String? = when (target) {
        KonanTarget.ANDROID_ARM32 -> "armv7a-linux-androideabi$ANDROID_API_LEVEL"
        KonanTarget.ANDROID_ARM64 -> "aarch64-linux-android$ANDROID_API_LEVEL"
        KonanTarget.ANDROID_X86 -> "i686-linux-android$ANDROID_API_LEVEL"
        KonanTarget.ANDROID_X64 -> "x86_64-linux-android$ANDROID_API_LEVEL"
        else -> null
    }

    private fun androidNdkArchDir(target: KonanTarget): String = when (target) {
        KonanTarget.ANDROID_ARM32 -> "arm-linux-androideabi"
        KonanTarget.ANDROID_ARM64 -> "aarch64-linux-android"
        KonanTarget.ANDROID_X86 -> "i686-linux-android"
        KonanTarget.ANDROID_X64 -> "x86_64-linux-android"
        else -> error("Unknown Android target: $target")
    }

    private fun androidNdkClang(target: KonanTarget): File? =
        androidNdkTriple(target)?.let { androidNdkDir(target).resolve("bin/$it-clang$exe") }

    /**
     * Apple SDKs are not downloadable (the `target-sysroot-xcode-*` dependencies are
     * `remote:internal`), so on a macOS host we use the SDKs from the installed Xcode,
     * exactly like Kotlin/Native itself does.
     */
    @Suppress("DEPRECATION")
    private fun appleSdkPath(target: KonanTarget): File? {
        val xcode = runCatching { Xcode.current }.getOrNull() ?: return null
        val path = when (target) {
            KonanTarget.MACOS_X64, KonanTarget.MACOS_ARM64 -> xcode.macosxSdk
            KonanTarget.IOS_ARM64 -> xcode.iphoneosSdk
            KonanTarget.IOS_X64, KonanTarget.IOS_SIMULATOR_ARM64 -> xcode.iphonesimulatorSdk
            KonanTarget.TVOS_ARM64 -> xcode.appletvosSdk
            KonanTarget.TVOS_X64, KonanTarget.TVOS_SIMULATOR_ARM64 -> xcode.appletvsimulatorSdk
            KonanTarget.WATCHOS_ARM32, KonanTarget.WATCHOS_ARM64 -> xcode.watchosSdk
            KonanTarget.WATCHOS_X64, KonanTarget.WATCHOS_SIMULATOR_ARM64 -> xcode.watchsimulatorSdk
            else -> return null
        }
        return File(path).takeIf { it.isDirectory }
    }

    private fun sysrootFor(target: KonanTarget): File? {
        if (HostManager.hostIsMac && target.family.isAppleFamily) {
            appleSdkPath(target)?.let { return it }
        }
        val raw = prop("targetSysRoot.${target.name}")
            ?: prop("dependencies.${HOST_TARGET.name}-${target.name}")
                ?.split("\n", "\\")
                ?.map { it.trim() }
                ?.firstOrNull { it.isNotEmpty() }
            ?: return null
        return when (target.family) {
            Family.ANDROID -> {
                val archDir = when (target) {
                    KonanTarget.ANDROID_ARM32 -> "arch-arm"
                    KonanTarget.ANDROID_ARM64 -> "arch-arm64"
                    KonanTarget.ANDROID_X86 -> "arch-x86"
                    KonanTarget.ANDROID_X64 -> "arch-x86_64"
                    else -> error("Unknown Android target: $target")
                }
                resolveKonanPath(raw).resolve("android-21").resolve(archDir)
            }

            else -> resolveKonanPath(raw)
        }
    }

    private fun clangArgsFor(target: KonanTarget): List<String>? {
        val triple = runCatching { target.clangTarget }.getOrNull() ?: return null
        val base = listOf("-O$optimizeLevel", "-target", triple, "-fexceptions")
        val familyArgs = when (target.family) {
            Family.LINUX -> {
                val tc = toolchainDir(target) ?: return null
                val sr = sysrootFor(target) ?: return null
                listOf(
                    "-isystem", LLVM_INCLUDE_DIR,
                    "-B$tc/$triple/bin",
                    "-fno-stack-protector",
                    "--gcc-toolchain=$tc",
                    "--sysroot=$sr",
                    "-fPIC",
                )
            }

            Family.MINGW -> {
                val sr = sysrootFor(target) ?: return null
                listOf(
                    "-isystem", LLVM_INCLUDE_DIR,
                    "-B$HOST_LLVM_BIN_FOLDER",
                    "-fno-stack-protector",
                    "--sysroot=$sr",
                )
            }

            Family.ANDROID -> {
                val sr = sysrootFor(target) ?: return null
                val ndk = androidNdkDir(target)
                listOf(
                    "-isystem", LLVM_INCLUDE_DIR,
                    "-B$ndk/bin",
                    "-fno-stack-protector",
                    "-fPIC",
                    "-D__ANDROID_API__=$ANDROID_API_LEVEL",
                    "--sysroot=$sr",
                    "-I$ndk/sysroot/usr/include/c++/v1",
                    "-I$ndk/sysroot/usr/include",
                    "-I$ndk/sysroot/usr/include/${androidNdkArchDir(target)}",
                )
            }

            Family.OSX, Family.IOS, Family.TVOS, Family.WATCHOS -> {
                val sr = sysrootFor(target) ?: return null
                listOf(
                    "-B$HOST_LLVM_BIN_FOLDER",
                    "-fno-stack-protector",
                    "--sysroot=$sr",
                )
            }
        }
        return base + familyArgs
    }

    private val clangs = HashMap<KonanTarget, CLang>()

    override fun findCppCompiler(target: KonanTarget): CppCompiler? {
        val args = clangArgsFor(target) ?: return null
        return clangs.getOrPut(target) {
            CLang(
                clangFile = LLVM_BIN,
                args = args + listOf("-c"),
                target = target,
                llvmBinFolder = HOST_LLVM_BIN_FOLDER,
            )
        }
    }

    override fun findLinked(target: KonanTarget): Linker? {
        val isAndroid = target.family == Family.ANDROID
        return CLangLinker(
            arFile = LLVM_AR_BIN_FOLDER,
            target = target,
            args = emptyList(),
            clangFile = if (isAndroid) (androidNdkClang(target) ?: LLVM_BIN) else LLVM_BIN,
            ldFile = LD_BIN,
            konanVersion = this,
            useNdkClang = isAndroid,
        )
    }

    override fun sysRoot(target: KonanTarget): String? =
        sysrootFor(target)?.absolutePath

    override fun gccToolchain(target: KonanTarget): String? =
        if (target.family == Family.LINUX) toolchainDir(target)?.absolutePath else null

    override fun findTargetInfo(target: KonanTarget): TargetInfo? {
        val triple = runCatching { target.clangTarget }.getOrNull() ?: return null
        val sr = sysrootFor(target) ?: return null
        val llvmDir = when (target.family) {
            Family.ANDROID -> androidNdkDir(target).resolve("bin")
            else -> HOST_LLVM_BIN_FOLDER
        }
        return TargetInfo(
            targetName = triple,
            sysRoot = listOf(sr),
            llvmDir = llvmDir,
            toolchain = if (target.family == Family.LINUX) toolchainDir(target) else null,
            clangCompileArgs = clangArgsFor(target) ?: emptyList(),
        )
    }
}
