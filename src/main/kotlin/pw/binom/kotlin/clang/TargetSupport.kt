package pw.binom.kotlin.clang

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

object TargetSupport {
    val hm = HostManager()
    fun isTargetSupport(target:KonanTarget)=
            targetInfoMap.containsKey(target)
//    private val MINGW_TARGETS = setOf(
//        KonanTarget.LINUX_ARM32_HFP,
//        KonanTarget.LINUX_ARM64,
//        KonanTarget.LINUX_MIPSEL32,
//        KonanTarget.LINUX_X64,
//        KonanTarget.LINUX_MIPS32,
//        KonanTarget.MINGW_X64,
//        KonanTarget.MINGW_X86,
//        KonanTarget.ANDROID_ARM32,
//        KonanTarget.ANDROID_ARM64,
//        KonanTarget.ANDROID_X86,
//        KonanTarget.ANDROID_X64,
//        KonanTarget.WASM32
//    )
//    private val LINUX_TARGETS = setOf(
//        KonanTarget.LINUX_ARM32_HFP,
//        KonanTarget.LINUX_ARM64,
//        KonanTarget.LINUX_MIPSEL32,
//        KonanTarget.LINUX_X64,
//        KonanTarget.LINUX_MIPS32,
//        KonanTarget.MINGW_X64,
//        KonanTarget.MINGW_X86,
//        KonanTarget.ANDROID_ARM32,
//        KonanTarget.ANDROID_ARM64,
//        KonanTarget.ANDROID_X86,
//        KonanTarget.ANDROID_X64,
//        KonanTarget.WASM32
//    )
//    private val MACOS_TARGETS = setOf(
//        KonanTarget.MACOS_X64,
//        KonanTarget.MACOS_ARM64,
//        KonanTarget.IOS_ARM32,
//        KonanTarget.IOS_ARM64,
//        KonanTarget.IOS_X64,
//        KonanTarget.IOS_SIMULATOR_ARM64,
//        KonanTarget.WATCHOS_ARM32,
//        KonanTarget.WATCHOS_ARM64,
//        KonanTarget.WATCHOS_X86,
//        KonanTarget.WATCHOS_X64,
//        KonanTarget.WATCHOS_SIMULATOR_ARM64,
//        KonanTarget.TVOS_ARM64,
//        KonanTarget.TVOS_X64,
//        KonanTarget.TVOS_SIMULATOR_ARM64,
//        KonanTarget.LINUX_X64,
//        KonanTarget.LINUX_ARM32_HFP,
//        KonanTarget.LINUX_ARM64,
//        KonanTarget.MINGW_X86,
//        KonanTarget.MINGW_X64,
//        KonanTarget.ANDROID_X86,
//        KonanTarget.ANDROID_X64,
//        KonanTarget.ANDROID_ARM32,
//        KonanTarget.ANDROID_ARM64,
//        KonanTarget.WASM32
//    )
    val hostTargets
        get() = hm.enabled
//    when {
//            HostManager.hostIsLinux -> LINUX_TARGETS
//            HostManager.hostIsMingw -> MINGW_TARGETS
//            HostManager.hostIsMac -> MACOS_TARGETS
//            else -> throw TargetSupportException("Target ${HostManager.host.name} not supported")
//        }

    fun isKonancTargetSupportOnHost(target: KonanTarget) =
        hm.isEnabled(target)
//        target in hostTargets
}