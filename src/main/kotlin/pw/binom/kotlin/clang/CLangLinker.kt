package pw.binom.kotlin.clang

import org.gradle.api.GradleScriptException
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class CLangLinker(val file: File, val target: KonanTarget, val args: List<String>) : Linker {
    override fun static(objectFiles: List<File>, output: File) {
        val builder = ProcessBuilder(
            listOf(file.path, "rc", output.path) + objectFiles.map { it.path } + args,
        )
        builder.directory(output.parentFile)
        builder.environment().put("PATH", "${file.parentFile.path};${System.getenv("PATH")}")
        val process = builder.start()
        StreamGobblerAppendable(process.inputStream, System.out, false).start()
        StreamGobblerAppendable(process.errorStream, System.err, false).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw GradleScriptException(
                "Can't execute link static library",
                RuntimeException("Can't link: Linked returns ${process.exitValue()}"),
            )
        }
    }

    override fun extract(archive: File, outputDirectory: File) {
        val builder = ProcessBuilder(
            listOf(file.path, "-x", archive.path) + args,
        )
        builder.directory(outputDirectory)
        val process = builder.start()
        StreamGobblerAppendable(process.inputStream, System.out, false).start()
        StreamGobblerAppendable(process.errorStream, System.err, false).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw GradleScriptException(
                "Can't execute link static library",
                RuntimeException("Can't link: Linked returns ${process.exitValue()}"),
            )
        }
    }
}
