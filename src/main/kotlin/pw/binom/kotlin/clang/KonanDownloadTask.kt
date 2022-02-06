package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Tasks for execute [Konan.checkKonanInstalled]
 */
abstract class KonanDownloadTask : DefaultTask() {
    @TaskAction
    fun execute() {
        Konan.checkKonanInstalled()
    }
}
