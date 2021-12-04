package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import java.io.File

abstract class CLangLinkTask: DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val librarySearchPaths: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val libraries: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val objects: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Input
    abstract val args: ListProperty<String>
}

fun startProcessAndWait(args:List<String>, workDirectory: File,envs:Map<String,String>):Int{
    val builder = ProcessBuilder(
        args
    )
    builder.directory(workDirectory)
    builder.environment().putAll(envs)
    val process = builder.start()
    StreamGobblerAppendable(process.inputStream, System.out, false).start()
    StreamGobblerAppendable(process.errorStream, System.err, false).start()
    process.waitFor()
    return process.exitValue()
}