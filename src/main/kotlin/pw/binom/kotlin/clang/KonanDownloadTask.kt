package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.util.internal.VersionNumber

/**
 * Tasks for execute [Konan.checkKonanInstalled]
 */
@DisableCachingByDefault(because = "Downloads external Kotlin/Native distribution, not safe to cache")
abstract class KonanDownloadTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val konanVersion: Property<String>

    private fun getKonanCompileVersion() =
        if (konanVersion.isPresent) {
            VersionNumber.parse(konanVersion.get())
        } else {
            KotlinVersions.V2_4_20
        }

    @TaskAction
    fun execute() {
        Konan.checkKonanInstalled(version = getKonanCompileVersion())
    }
}
