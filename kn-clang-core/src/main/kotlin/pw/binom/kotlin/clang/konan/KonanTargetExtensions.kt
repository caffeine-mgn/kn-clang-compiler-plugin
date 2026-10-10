package pw.binom.kotlin.clang.konan

import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
private const val IOS_VERSION="14.0"
private const val WATCHOS_VERSION="7.0"

/**
 * Subdirectory of `<jdk>/include/` that holds the per-platform `jni_md.h` for
 * this Kotlin/Native target. Used by `JdkIncludeResolver` to wire the JVM
 * JNI include path automatically — see `clangBuildJni` in the `kn-clang` module.
 *
 * Linux and Android share the Linux JNI headers (OpenJDK ships one
 * `jni_md.h` per ABI, not per API); Apple targets use the darwin headers,
 * and MinGW uses the Win32 headers.
 */
val KonanTarget.jdkIncludePlatform: String
    get() = when (family) {
        Family.LINUX, Family.ANDROID -> "linux"
        Family.OSX -> "darwin"
        Family.MINGW -> "win32"
        else -> "linux"
    }

val KonanTarget.clangTarget
    get()=when(this){
        KonanTarget.ANDROID_ARM32 -> "arm-unknown-linux-androideabi"
        KonanTarget.ANDROID_ARM64 -> "aarch64-unknown-linux-android"
        KonanTarget.ANDROID_X64 -> "x86_64-unknown-linux-android"
        KonanTarget.ANDROID_X86 -> "i686-unknown-linux-android"
        KonanTarget.LINUX_ARM64 -> "aarch64-unknown-linux-gnu"
        KonanTarget.LINUX_X64 -> "x86_64-unknown-linux-gnu"
        KonanTarget.MINGW_X64 -> "x86_64-pc-windows-gnu"
        KonanTarget.MACOS_ARM64 -> "arm64-apple-darwin23.4.0"
        KonanTarget.MACOS_X64 -> "x86_64-apple-macos13"
        KonanTarget.IOS_ARM64 -> "arm64-apple-ios$IOS_VERSION"
        KonanTarget.IOS_SIMULATOR_ARM64 -> "arm64-apple-ios$IOS_VERSION-simulator"
        KonanTarget.IOS_X64 -> "x86_64-apple-ios$IOS_VERSION"
        KonanTarget.LINUX_ARM32_HFP -> "arm-linux-gnueabihf"
        KonanTarget.TVOS_ARM64 -> "arm64-apple-tvos$IOS_VERSION"
        KonanTarget.TVOS_SIMULATOR_ARM64 -> "arm64-apple-tvos$IOS_VERSION-simulator"
        KonanTarget.TVOS_X64 -> "x86_64-apple-tvos$IOS_VERSION"
        KonanTarget.WATCHOS_ARM32 -> "armv7k-apple-watchos$WATCHOS_VERSION"
        KonanTarget.WATCHOS_DEVICE_ARM64,
        KonanTarget.WATCHOS_ARM64 -> "arm64-apple-watchos$WATCHOS_VERSION"
        KonanTarget.WATCHOS_SIMULATOR_ARM64 -> "arm64-apple-watchos$WATCHOS_VERSION-simulator"
        KonanTarget.WATCHOS_X64 -> "x86_64-apple-watchos$WATCHOS_VERSION"
    }