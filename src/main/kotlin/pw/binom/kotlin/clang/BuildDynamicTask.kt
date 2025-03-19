package pw.binom.kotlin.clang

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.target.HostManager

abstract class BuildDynamicTask : BuildTask() {

    @get:OutputFile
    abstract val staticFile: RegularFileProperty

    @TaskAction
    fun execute() {
        if (!TargetSupport.isKonancTargetSupportOnHost(target.get())) {
            logger.warn("Compile target ${target.get()} not supported on host ${HostManager.host.name}")
            return
        }
        if (!staticFile.isPresent) {
            throw InvalidUserDataException("Static output file not set")
        }
        compileAll()


        val konan = KonanVersion.getVersion(getKonanCompileVersion())
        val linker = konan.getLinked(selectedTarget)

        linker.static(
            objectFiles = compiles.filterNotNull().map {
                it.objectFile
            },
            output = staticFile.asFile.get(),
        )
    }
}
