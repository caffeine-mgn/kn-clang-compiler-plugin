package pw.binom.kotlin.clang

import org.gradle.api.GradleScriptException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.konan.target.KonanTarget

abstract class BuildWasm32 : CLangLinkTask() {


    //no-entry

    @get:Optional
    @get:Input
    abstract val entryPoint: Property<String> //--entry --no-entry

    @get:Input
    abstract val exports: ListProperty<String>

    @get:Input
    abstract val exportAll: Property<Boolean>


    /**
     * Optimization level for LTO
     */
    @get:Optional
    @get:Input
    abstract val ltoOptimizationLevel: Property<Int>//--lto-O3

    @TaskAction
    fun execute() {
        Konan.checkSysrootInstalled(KonanTarget.WASM32)
        val ldPath = HOST_LLVM_BIN_FOLDER.resolve("wasm-ld").executable
        val args = ArrayList<String>()
        args += ldPath.absolutePath

        if (ltoOptimizationLevel.isPresent) {
            args += "--lto-O${ltoOptimizationLevel.get()}"
        }
        if (entryPoint.isPresent) {
            args += "--entry"
            args += entryPoint.get()
        }
        if (exports.get().isEmpty()) {
            args += "--no-entry"
        } else {
            exports.get().forEach { symbol ->
                args += "--export=$symbol"
            }
        }

        librarySearchPaths.forEach { libPath ->
            args += "-L"
            args += libPath.absolutePath
        }

        libraries.forEach { lib ->
            args += "-l"
            args += lib.absolutePath
        }
        val outFile = output.get().asFile
        args += "-o"
        args += outFile.name

        objects.forEach {
            args += it.absolutePath
        }

        this.args.get().forEach {
            args += it
        }

        println("Args: ${args}")

        val exitCode = startProcessAndWait(
            args=args,
            workDirectory = outFile.parentFile,
            envs = mapOf("PATH" to "$HOST_LLVM_BIN_FOLDER;${System.getenv("PATH")}")
        )
//        val builder = ProcessBuilder(
//            args
//        )
//        builder.directory(outFile.parentFile)
//        builder.environment().put("PATH", "$HOST_LLVM_BIN_FOLDER;${System.getenv("PATH")}")
//        val process = builder.start()
//        StreamGobblerAppendable(process.inputStream, System.out, false).start()
//        StreamGobblerAppendable(process.errorStream, System.err, false).start()
//        process.waitFor()
        if (exitCode != 0) {
            throw GradleScriptException(
                "Can't execute link static library",
                RuntimeException("Can't link: Linked returns $exitCode")
            )
        }
    }
}