package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.target.KonanTarget

abstract class KonanDownloadToolchainTask : DefaultTask() {
    @get:Input
    abstract val target: Property<KonanTarget>

    @TaskAction
    fun execute() {
        Konan.checkSysrootInstalled(target.get())
    }
}
