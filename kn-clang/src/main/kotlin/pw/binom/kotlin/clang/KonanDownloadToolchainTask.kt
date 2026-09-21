package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import pw.binom.kotlin.clang.KonanVersionNumber
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.konan.target.KonanTarget

@DisableCachingByDefault(because = "Downloads external sysroot/toolchain, not safe to cache")
abstract class KonanDownloadToolchainTask : DefaultTask() {
    @get:Input
    abstract val target: Property<KonanTarget>

    @get:Input
    @get:Optional
    abstract val konanVersion: Property<String>

    private fun getKonanCompileVersion() =
        if (konanVersion.isPresent) {
            KonanVersionNumber.parse(konanVersion.get())
        } else {
            KotlinVersions.V2_4_20
        }

    @TaskAction
    fun execute() {
        Konan.checkSysrootInstalled(version = getKonanCompileVersion(), target = target.get())
    }
}
