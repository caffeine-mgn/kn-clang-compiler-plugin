package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Tasks for execute [Konan.checkKonanInstalled]
 */
abstract class KonanDownloadTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val konanVersion: Property<String>

    private fun getKonanCompileVersion() =
        if (konanVersion.isPresent) {
            konanVersion.get()
        } else {
            KotlinVersion.CURRENT.toString()
        }

    @TaskAction
    fun execute() {
        Konan.checkKonanInstalled(version = getKonanCompileVersion())
    }
}
