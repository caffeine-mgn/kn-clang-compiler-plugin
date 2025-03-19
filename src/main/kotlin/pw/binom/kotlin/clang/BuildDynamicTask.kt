package pw.binom.kotlin.clang

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.target.HostManager

abstract class BuildDynamicTask : BuildTask() {

    @get:OutputFile
    abstract val dynamicFile: RegularFileProperty

    @get:Input
    val linkArgs = ArrayList<String>()

    fun linkArgs(vararg args: String) {
        linkArgs += args.toList()
    }

    @TaskAction
    fun execute() {
        if (!TargetSupport.isKonancTargetSupportOnHost(target.get())) {
            logger.warn("Compile target ${target.get()} not supported on host ${HostManager.host.name}")
            return
        }
        if (!dynamicFile.isPresent) {
            throw InvalidUserDataException("Static output file not set")
        }
        compileAll()


        val konan = KonanVersion.getVersion(getKonanCompileVersion())
        val linker = konan.getLinked(selectedTarget)

        linker.dynamic(
            objectFiles = compiles.filterNotNull().map {
                it.objectFile
            },
            output = dynamicFile.asFile.get(),
            linkArgs = linkArgs,
        )
    }
}
