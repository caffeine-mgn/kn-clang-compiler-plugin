package pw.binom.kotlin.clang

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class Toolchain(
    val clang: File,
    val ar: File,
)


val KONAN_USER_DIR = File(System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan")
val WASM32_SYSROOT_NAME = "target-sysroot-4-embedded"
val WASM32_TOOL_CHAIN_NAME = "target-toolchain-2-mingw-wasm"
val MINGW_TOOLCHAIN = Toolchain(
    clang = KONAN_USER_DIR.resolve("llvm-11.1.0-windows-x64-essentials/bin/clang.exe"),
    ar = KONAN_USER_DIR.resolve("llvm-11.1.0-windows-x64-essentials/bin/llvm-ar.exe"),
)

val ANDROID_WINDOWS_TOOLCHAIN = Toolchain(
    clang = KONAN_USER_DIR.resolve("target-toolchain-2-windows-android_ndk/bin/clang.exe"),
    ar = KONAN_USER_DIR.resolve("target-toolchain-2-windows-android_ndk/bin/llvm-ar.exe"),
)

private val LINUX_X64_SYSROOT = "x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2"

val KONAN_DEPS = KONAN_USER_DIR.resolve("dependencies")
val HOST_KONAN_LLVM_DIR_NAME = when {
    HostManager.hostIsLinux -> "llvm-11.1.0-linux-x64-essentials"
    HostManager.hostIsMac -> "apple-llvm-20200714-macos-x64-essentials"
    HostManager.hostIsMingw -> "llvm-11.1.0-windows-x64-essentials"
    else -> error("Unknown host OS")
}

private val ANDROID_KONAN_LLVM_DIR_NAME = when {
    HostManager.hostIsLinux -> "target-toolchain-2-linux-android_ndk"
    HostManager.hostIsMac -> "target-toolchain-2-osx-android_ndk"
    HostManager.hostIsMingw -> "target-toolchain-2-windows-android_ndk"
    else -> error("Unknown host OS")
}

private val ANDROID_SYSROOT_DIR_BASE_1 = KONAN_DEPS.resolve(ANDROID_KONAN_LLVM_DIR_NAME).resolve("sysroot")
private val ANDROID_SYSROOT_DIR_TARGETS_2 = KONAN_DEPS.resolve("target-sysroot-1-android_ndk").resolve("android-21")

val PREBUILD_KONAN_DIR_NAME = when {
    HostManager.hostIsLinux -> "kotlin-native-prebuilt-linux-x86_64-1.6.10"
    HostManager.hostIsMac -> "kotlin-native-prebuilt-macos-x86_64-1.6.10"
    HostManager.hostIsMingw -> "kotlin-native-prebuilt-windows-x86_64-1.6.10"
    else -> error("Unknown host OS")
}

val HOST_LLVM_BIN_FOLDER = KONAN_DEPS.resolve("$HOST_KONAN_LLVM_DIR_NAME/bin")
val ANDROID_LLVM_BIN_FOLDER = KONAN_DEPS.resolve("$ANDROID_KONAN_LLVM_DIR_NAME/bin")
val MINGW_X86_SYSROOT = "msys2-mingw-w64-i686-1"

data class TargetInfo(
    val targetName: String,
    val sysRoot: List<File>,
    val clangCompileArgs: List<String> = emptyList(),
    val llvmDir: File,
    val toolchain: File? = null,
)

val LINUX_ARM64 = "aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2"
val targetInfoMap = mapOf(
    KonanTarget.LINUX_X64 to TargetInfo(
        targetName = "x86_64-unknown-linux-gnu",
        sysRoot = listOf(KONAN_DEPS.resolve("$LINUX_X64_SYSROOT/x86_64-unknown-linux-gnu/sysroot")),
        llvmDir = HOST_LLVM_BIN_FOLDER,
        clangCompileArgs = listOf("-fPIC"),
        toolchain = KONAN_DEPS.resolve(LINUX_X64_SYSROOT)
//                1.4.32 - sysRoot = konanDeps.resolve("target-gcc-toolchain-3-linux-x86-64/x86_64-unknown-linux-gnu/sysroot")
    ),
    KonanTarget.MACOS_X64 to TargetInfo(
        targetName = "x86_64-apple-macosx",
        sysRoot = listOf(KONAN_DEPS.resolve("target-sysroot-10-macos_x64")),
        clangCompileArgs = listOf("-march=x86-64"),
        llvmDir = HOST_LLVM_BIN_FOLDER,
    ),
    KonanTarget.MINGW_X64 to TargetInfo(
        targetName = "x86_64-pc-windows-gnu",
        sysRoot = listOf(KONAN_DEPS.resolve("msys2-mingw-w64-x86_64-1")),
        llvmDir = HOST_LLVM_BIN_FOLDER,
    ),
    KonanTarget.MINGW_X86 to TargetInfo(
        targetName = "i686-w64-mingw32",
        sysRoot = listOf(KONAN_DEPS.resolve(MINGW_X86_SYSROOT)),
        llvmDir = HOST_LLVM_BIN_FOLDER,
    ),
    KonanTarget.LINUX_MIPSEL32 to TargetInfo(
        targetName = "mipsel-unknown-linux-gnu",
        sysRoot = listOf(KONAN_DEPS.resolve("target-sysroot-2-mipsel")),
        clangCompileArgs = listOf("-mfpu=vfp", "-mfloat-abi=hard"),
        llvmDir = HOST_LLVM_BIN_FOLDER,
    ),
    KonanTarget.LINUX_ARM32_HFP to TargetInfo(
        targetName = "armv6-unknown-linux-gnueabihf",
        sysRoot = listOf(KONAN_DEPS.resolve("arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2/arm-unknown-linux-gnueabihf/sysroot")),
        clangCompileArgs = listOf("-mfpu=vfp", "-mfloat-abi=hard"),
        llvmDir = HOST_LLVM_BIN_FOLDER,
        toolchain = KONAN_DEPS.resolve("arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2")
    ),
    KonanTarget.LINUX_ARM64 to TargetInfo(
        targetName = "aarch64-unknown-linux-gnu",
        sysRoot = listOf(KONAN_DEPS.resolve("$LINUX_ARM64/aarch64-unknown-linux-gnu/sysroot")),
        clangCompileArgs = listOf(/*"-mfpu=vfp", "-mfloat-abi=hard", */"-fPIC"),
        llvmDir = HOST_LLVM_BIN_FOLDER,
        toolchain = KONAN_DEPS.resolve(LINUX_ARM64)
    ),
    KonanTarget.ANDROID_ARM32 to TargetInfo(
        targetName = "arm-linux-androideabi",
        sysRoot = listOf(ANDROID_SYSROOT_DIR_TARGETS_2.resolve("arch-arm"), ANDROID_SYSROOT_DIR_BASE_1),
        llvmDir = ANDROID_LLVM_BIN_FOLDER,
    ),
    KonanTarget.ANDROID_ARM64 to TargetInfo(
        targetName = "aarch64-linux-android",
        sysRoot = listOf(ANDROID_SYSROOT_DIR_TARGETS_2.resolve("arch-arm64"), ANDROID_SYSROOT_DIR_BASE_1),
        llvmDir = ANDROID_LLVM_BIN_FOLDER,
    ),
    KonanTarget.ANDROID_X86 to TargetInfo(
        targetName = "i686-linux-android",
        sysRoot = listOf(ANDROID_SYSROOT_DIR_TARGETS_2.resolve("arch-x86"), ANDROID_SYSROOT_DIR_BASE_1),
        llvmDir = ANDROID_LLVM_BIN_FOLDER,
    ),
    KonanTarget.ANDROID_X64 to TargetInfo(
        targetName = "x86_64-linux-android",
        sysRoot = listOf(ANDROID_SYSROOT_DIR_TARGETS_2.resolve("arch-x86_64"), ANDROID_SYSROOT_DIR_BASE_1),
        llvmDir = ANDROID_LLVM_BIN_FOLDER,
    ),
    KonanTarget.WASM32 to TargetInfo(
        targetName = "wasm32-unknown-unknown",//"wasm32",
        sysRoot = listOf(KONAN_DEPS.resolve(WASM32_SYSROOT_NAME)),
        llvmDir = HOST_LLVM_BIN_FOLDER,
        clangCompileArgs = listOf(
            "-fPIC",
            "-fno-rtti",
            "-fvisibility=default",
            "-D_LIBCPP_ABI_VERSION=2",
            "-D_LIBCPP_NO_EXCEPTIONS=1",
            "-nostdinc",
            "-Xclang",
            "-nobuiltininc",
            "-Xclang",
            "-nostdsysteminc",
            "-BC" + KONAN_DEPS.resolve(WASM32_TOOL_CHAIN_NAME).resolve("bin").absolutePath,
            "-isystem${KONAN_DEPS.resolve(HOST_KONAN_LLVM_DIR_NAME)}/lib/clang/11.1.0/include",
            "-Xclang",
            "-isystem${KONAN_DEPS.resolve(WASM32_SYSROOT_NAME).resolve("include/libcxx")}",
            "-Xclang",
            "-isystem${KONAN_DEPS.resolve(WASM32_SYSROOT_NAME).resolve("lib/libcxxabi/include")}",
            "-Xclang",
            "-isystem${KONAN_DEPS.resolve(WASM32_SYSROOT_NAME).resolve("include/compat")}",
            "-Xclang",
            "-isystem${KONAN_DEPS.resolve(WASM32_SYSROOT_NAME).resolve("include/libc")}",
        ),
    )
)