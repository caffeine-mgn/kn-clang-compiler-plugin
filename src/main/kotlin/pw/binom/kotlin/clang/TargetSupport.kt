package pw.binom.kotlin.clang

import org.gradle.util.internal.VersionNumber
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget

object TargetSupport {
    private val hostManager = HostManager()

    fun isKonanTargetEnabledOnHost(target: KonanTarget): Boolean =
        hostManager.isEnabled(target)

    fun isTargetConfigured(
        target: KonanTarget,
        version: VersionNumber,
    ): Boolean = KonanVersion.getVersion(version).findTargetInfo(target) != null
}
