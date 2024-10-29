package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.target.KonanTarget

abstract class KonanDownloadToolchainTask : DefaultTask() {
    @get:Input
    abstract val target: Property<KonanTarget>

    @get:Input
    @get:Optional
    abstract val konanVersion: Property<String>

    private fun getKonanCompileVersion() =
        if (konanVersion.isPresent) {
            Version(konanVersion.get())
        } else {
            Version(KotlinVersion.CURRENT.toString())
        }

    @TaskAction
    fun execute() {
        Konan.checkSysrootInstalled(version = getKonanCompileVersion(), target = target.get())
    }
}
