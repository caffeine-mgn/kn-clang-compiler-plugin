package pw.binom.kotlin.clang.konan

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.CLang
import pw.binom.kotlin.clang.CLangLinker
import pw.binom.kotlin.clang.CppCompiler
import pw.binom.kotlin.clang.KonanVersion
import java.io.File

abstract class BaseKonanVersion : KonanVersion {
    companion object {
        val exe = if (HostManager.hostIsMingw) ".exe" else ""
        val arch = System.getProperty("os.arch")
        val HOST_KONAN_LLVM11_DIR_NAME
            get() = when {
                HostManager.hostIsLinux -> "llvm-11.1.0-linux-x64-essentials"
                HostManager.hostIsMac && arch == "aarch64" -> "apple-llvm-20200714-macos-aarch64-essentials"
                HostManager.hostIsMac -> "apple-llvm-20200714-macos-x64-essentials"
                HostManager.hostIsMingw -> "llvm-11.1.0-windows-x64-essentials"
                else -> error("Unknown host OS")
            }
        val HOST_KONAN_LLVM16_DIR_NAME
            get() = when {
                HostManager.hostIsLinux -> "llvm-16.0.0-x86_64-linux-essentials-80"
                HostManager.hostIsMac && arch == "aarch64" -> "apple-llvm-20200714-macos-aarch64-essentials"
                HostManager.hostIsMac -> "apple-llvm-20200714-macos-x64-essentials"
                HostManager.hostIsMingw -> "llvm-16.0.0-x86_64-windows-essentials-56"
                else -> error("Unknown host OS")
            }
    }

    protected abstract val HOST_KONAN_LLVM_DIR_NAME: String
    private val ANDROID_TOOLCHAIN_DIR_NAME = when {
        HostManager.hostIsLinux -> "target-toolchain-2-linux-android_ndk"
        HostManager.hostIsMac -> "target-toolchain-2-osx-android_ndk"
        HostManager.hostIsMingw -> "target-toolchain-2-windows-android_ndk"
        else -> error("Unknown host OS")
    }
    val KONAN_DATA_FOLDER = File(System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan")
    val DEPENDENCIES_FOLDER = KONAN_DATA_FOLDER.resolve("dependencies")
    val LLVM_BIN = DEPENDENCIES_FOLDER.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/clang$exe")
    val LD_BIN = DEPENDENCIES_FOLDER.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/lld$exe")
    val LLVM_AR_BIN_FOLDER = DEPENDENCIES_FOLDER.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/llvm-ar$exe")
    val optimizeLevel = 2

    protected abstract val LLVM_VERSION: String
    private val androidArm32 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-arm",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/arm-linux-androideabi",
    )

    /*
/home/subochev/.konan/dependencies/llvm-11.1.0-linux-x64-essentials/bin/clang
-O2
-fexceptions
-isystem
/home/subochev/.konan/dependencies/llvm-11.1.0-linux-x64-essentials/lib/clang/$LLVM_VERSION/include
-B/home/subochev/.konan/dependencies/target-toolchain-2-linux-android_ndk/bin
-fno-stack-protector
-target
arm-unknown-linux-androideabi
-fPIC
-D__ANDROID_API__=21
--sysroot=/home/subochev/.konan/dependencies/target-sysroot-1-android_ndk/android-21/arch-arm
-I/home/subochev/.konan/dependencies/target-toolchain-2-linux-android_ndk/sysroot/usr/include/c++/v1
-I/home/subochev/.konan/dependencies/target-toolchain-2-linux-android_ndk/sysroot/usr/include
-I/home/subochev/.konan/dependencies/target-toolchain-2-linux-android_ndk/sysroot/usr/include/arm-linux-androideabi
-Wno-builtin-macro-redefined
     */

    private val androidArm64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-arm64",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/aarch64-linux-android",
    )

    private val androidX64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-x86_64",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/x86_64-linux-android",
    )

    private val androidX86 = listOf(
        "-fexceptions",
        "-isystem", "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-x86",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/i686-linux-android",
//        "-Wno-builtin-macro-redefined"
    )

    private val linuxArm64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/bin",
        "-fno-stack-protector",
        "--gcc-toolchain=$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2",
        "--sysroot=$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/sysroot",
        "-fPIC",
    )

    private val linuxX64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/bin",
        "-fno-stack-protector",
        "--gcc-toolchain=$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2",
        "--sysroot=$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/sysroot",
        "-fPIC",
    )

    val mingwX64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/bin",
        "-fno-stack-protector",
        "--sysroot=$DEPENDENCIES_FOLDER/msys2-mingw-w64-x86_64-2",
    )
    val maxOsArm64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/bin",
        "-fno-stack-protector",
//        "-I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/clang/15.0.0/include",
//        "-I/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include",
//        "-I/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/include",
        "-isystem",
        "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include",
        "-isystem",
        "/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include/malloc",
    )
    val maxOsX64 = listOf(
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/$LLVM_VERSION/include",
        "-B$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/bin",
        "-fno-stack-protector",
        "-I/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/usr/include",
    )

    private val clangs = HashMap<KonanTarget, CLang>()
    override fun gccToolchain(target: KonanTarget): String? =
        when (target) {
            KonanTarget.LINUX_ARM64 -> "$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2"
            KonanTarget.LINUX_X64 -> "$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2"
            KonanTarget.ANDROID_ARM32,
            KonanTarget.ANDROID_ARM64,
            KonanTarget.ANDROID_X64,
            KonanTarget.ANDROID_X86,
            KonanTarget.MINGW_X64,
            KonanTarget.MACOS_ARM64,
            KonanTarget.MACOS_X64,
                -> null

            else -> TODO()
        }
    override fun sysRoot(target: KonanTarget) =
        when (target) {
            KonanTarget.ANDROID_ARM32 -> "$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-arm"
            KonanTarget.ANDROID_ARM64 -> "$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-arm64"
            KonanTarget.ANDROID_X64 -> "$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-x86_64"
            KonanTarget.ANDROID_X86 -> "$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-x86"
            KonanTarget.LINUX_ARM64 -> "$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/sysroot"
            KonanTarget.LINUX_X64 -> "$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/sysroot"
            KonanTarget.MINGW_X64 -> "$DEPENDENCIES_FOLDER/msys2-mingw-w64-x86_64-2"
            KonanTarget.MACOS_ARM64,
            KonanTarget.MACOS_X64,
                -> null

            else -> TODO()
        }

    fun getClangArgs(target: KonanTarget) =
        when (target) {
            KonanTarget.ANDROID_ARM32 -> androidArm32
            KonanTarget.ANDROID_ARM64 -> androidArm64
            KonanTarget.ANDROID_X64 -> androidX64
            KonanTarget.ANDROID_X86 -> androidX86
            KonanTarget.LINUX_ARM64 -> linuxArm64
            KonanTarget.LINUX_X64 -> linuxX64
            KonanTarget.MINGW_X64 -> mingwX64
            KonanTarget.MACOS_ARM64 -> maxOsArm64
            KonanTarget.MACOS_X64 -> maxOsX64
            else -> null
        }?.let {
            listOf(
                "-O$optimizeLevel",
                "-target",
                target.clangTarget,
            ) + it
        }

    override fun findCppCompiler(target: KonanTarget): CppCompiler? {
        val args = getClangArgs(target) ?: return null
        return clangs.getOrPut(target) {
            CLang(
                clangFile = LLVM_BIN,
                args = args + listOf("-c"),
                target = target,
            )
        }
    }

    override fun findLinked(target: KonanTarget) =
        CLangLinker(
            arFile = LLVM_AR_BIN_FOLDER,
            target = target,
            args = emptyList(),
            clangFile = LLVM_BIN,
            ldFile = LD_BIN,
            konanVersion = this,
        )
}