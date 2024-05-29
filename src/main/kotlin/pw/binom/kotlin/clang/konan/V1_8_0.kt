package pw.binom.kotlin.clang.konan

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.*
import java.io.File

abstract class BaseKonanVersion : KonanVersion {
    private val exe = if (HostManager.hostIsMingw) ".exe" else ""
    private val arch = System.getProperty("os.arch")
    val HOST_KONAN_LLVM_DIR_NAME = when {
        HostManager.hostIsLinux -> "llvm-11.1.0-linux-x64-essentials"
        HostManager.hostIsMac && arch == "aarch64" -> "apple-llvm-20200714-macos-aarch64-essentials"
        HostManager.hostIsMac -> "apple-llvm-20200714-macos-x64-essentials"
        HostManager.hostIsMingw -> "llvm-11.1.0-windows-x64-essentials"
        else -> error("Unknown host OS")
    }
    private val ANDROID_TOOLCHAIN_DIR_NAME = when {
        HostManager.hostIsLinux -> "target-toolchain-2-linux-android_ndk"
        HostManager.hostIsMac -> "target-toolchain-2-osx-android_ndk"
        HostManager.hostIsMingw -> "target-toolchain-2-windows-android_ndk"
        else -> error("Unknown host OS")
    }
    val KONAN_DATA_FOLDER = File(System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan")
    val DEPENDENCIES_FOLDER = KONAN_DATA_FOLDER.resolve("dependencies")
    val LLVM_BIN = DEPENDENCIES_FOLDER.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/clang$exe")
    val LLVM_AR_BIN_FOLDER = DEPENDENCIES_FOLDER.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin/llvm-ar$exe")
    val optimizeLevel = 2

    private val androidArm32 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "arm-unknown-linux-androideabi",
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
/home/subochev/.konan/dependencies/llvm-11.1.0-linux-x64-essentials/lib/clang/11.1.0/include
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
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "aarch64-unknown-linux-android",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-arm64",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/aarch64-linux-android",
    )

    private val androidX64 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "x86_64-unknown-linux-android",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-x86_64",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/x86_64-linux-android",
    )

    private val androidX86 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem", "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "i686-unknown-linux-android",
        "-fPIC",
        "-D__ANDROID_API__=21",
        "--sysroot=$DEPENDENCIES_FOLDER/target-sysroot-1-android_ndk/android-21/arch-x86",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/c++/v1",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include",
        "-I$DEPENDENCIES_FOLDER/$ANDROID_TOOLCHAIN_DIR_NAME/sysroot/usr/include/i686-linux-android",
//        "-Wno-builtin-macro-redefined"
    )

    private val linuxArm64 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/bin",
        "-fno-stack-protector",
        "--gcc-toolchain=$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2",
        "-target",
        "aarch64-unknown-linux-gnu",
        "--sysroot=$DEPENDENCIES_FOLDER/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/sysroot",
        "-fPIC",
    )

    private val linuxX64 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/bin",
        "-fno-stack-protector",
        "--gcc-toolchain=$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2",
        "-target",
        "x86_64-unknown-linux-gnu",
        "--sysroot=$DEPENDENCIES_FOLDER/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/sysroot",
        "-fPIC",
    )

    val mingwX64 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "x86_64-pc-windows-gnu",
        "--sysroot=$DEPENDENCIES_FOLDER/msys2-mingw-w64-x86_64-2",
    )
    val maxOsArm64 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "arm64-apple-darwin20.1.0"
    )
    val maxOsX64 = listOf(
        "-O$optimizeLevel",
        "-fexceptions",
        "-isystem",
        "$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/lib/clang/11.1.0/include",
        "-B$DEPENDENCIES_FOLDER/$HOST_KONAN_LLVM_DIR_NAME/bin",
        "-fno-stack-protector",
        "-target",
        "x86_64-apple-macos13",
    )

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
        }

    private val clangs = HashMap<KonanTarget, CLang>()
    override fun findCppCompiler(target: KonanTarget): CppCompiler? {
        val args = getClangArgs(target) ?: return null
        return clangs.getOrPut(target) {
            CLang(
                file = LLVM_BIN,
                args = args + listOf("-c"),
                target = target,
            )
        }
    }

    override fun findLinked(target: KonanTarget) =
        CLangLinker(
            file = LLVM_AR_BIN_FOLDER,
            target = target,
            args = emptyList()
        )
}

// -Wno-builtin-macro-redefined

object V1_8_0 : BaseKonanVersion() {
    private val targets = mapOf(
        KonanTarget.MINGW_X64 to TargetInfo(
            targetName = "x86_64-pc-windows-gnu",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("msys2-mingw-w64-x86_64-2")),
            llvmDir = LLVM_BIN,
        ),
        KonanTarget.MINGW_X86 to TargetInfo(
            targetName = "i686-w64-mingw32",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("msys2-mingw-w64-i686-2")),
            llvmDir = LLVM_BIN,
        ),
        KonanTarget.LINUX_X64 to TargetInfo(
            targetName = "x86_64-unknown-linux-gnu",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("$LINUX_X64_SYSROOT/x86_64-unknown-linux-gnu/sysroot")),
            llvmDir = LLVM_BIN,
            clangCompileArgs = listOf("-fPIC"),
            toolchain = DEPENDENCIES_FOLDER.resolve(LINUX_X64_SYSROOT),
        ),
        KonanTarget.LINUX_ARM32_HFP to TargetInfo(
            targetName = "armv6-unknown-linux-gnueabihf",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2/arm-unknown-linux-gnueabihf/sysroot")),
            clangCompileArgs = listOf("-mfpu=vfp", "-mfloat-abi=hard"),
            llvmDir = LLVM_BIN,
            toolchain = DEPENDENCIES_FOLDER.resolve("arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2"),
        ),
        KonanTarget.LINUX_ARM64 to TargetInfo(
            targetName = "aarch64-unknown-linux-gnu",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("$LINUX_ARM64/aarch64-unknown-linux-gnu/sysroot")),
            clangCompileArgs = listOf(/*"-mfpu=vfp", "-mfloat-abi=hard", */"-fPIC"),
            llvmDir = LLVM_BIN,
            toolchain = DEPENDENCIES_FOLDER.resolve(LINUX_ARM64),
        ),
    )

    override fun findTargetInfo(target: KonanTarget): TargetInfo? = targets[target]
}
