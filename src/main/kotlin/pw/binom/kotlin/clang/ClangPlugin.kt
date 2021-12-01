package pw.binom.kotlin.clang

import org.gradle.api.Plugin
import org.gradle.api.Project

class ClangPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val downloadKonan = target.tasks.register("downloadKonan", KonanDownloadTask::class.java).get()
        downloadKonan.description = "Downloads konan to user home directory"
        downloadKonan.group = "konan"
        TargetSupport.hostTargets.forEach {
            val task = target.tasks.register(
                "downloadKonanToolchain${it.name.capitalize()}",
                KonanDownloadToolchainTask::class.java
            ).get()
            task.description = "Downloads konan toolchain for target ${it.name}"
            downloadKonan.group = "konan"
        }
    }
}