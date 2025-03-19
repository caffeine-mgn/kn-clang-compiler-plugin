package pw.binom.kotlin.clang

import org.gradle.api.GradleScriptException
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.konan.clangTarget
import java.io.File

class CLangLinker(
    val arFile: File,
    val clangFile: File,
    val ldFile: File,
    val target: KonanTarget,
    val args: List<String>,
    val konanVersion: KonanVersion,
) : Linker {
    override fun static(objectFiles: List<File>, output: File) {
        val builder = ProcessBuilder(
            listOf(arFile.path, "rc", output.path) + objectFiles.map { it.path } + args,
        )
        builder.directory(output.parentFile)
        builder.environment().put("PATH", "${arFile.parentFile.path};${System.getenv("PATH")}")
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

    override fun dynamic(objectFiles: List<File>, output: File, linkArgs: List<String>) {
        val sharedArg = when (target.family) {
            Family.MINGW,
            Family.ANDROID,
            Family.LINUX,
                -> "-shared"

            Family.OSX,
            Family.IOS,
            Family.TVOS,
            Family.WATCHOS,
                -> "-dynamiclib"
        }
        val addArgs = ArrayList<String>()
        addArgs += when (target.family) {
            Family.MINGW,
            Family.ANDROID,
            Family.LINUX,
                -> listOf()

            Family.OSX,
            Family.IOS,
            Family.TVOS,
            Family.WATCHOS,
                -> listOf()
        }
        val sysRoot = konanVersion.sysRoot(target)
        val gccToolchain = konanVersion.gccToolchain(target)
        if (sysRoot != null) {
            addArgs += "--sysroot=$sysRoot"
        }
        if (gccToolchain != null) {
            addArgs += "--gcc-toolchain=$gccToolchain"
        }
        val commands = listOf(
            clangFile.path,
            "-fuse-ld=lld",
            sharedArg,
            "-o",
            output.absolutePath,
            "-target",
            target.clangTarget,
        ) + args + linkArgs + addArgs + objectFiles.map { it.absolutePath }
        println("Linking shared lib with cmd: $commands")
        val builder = ProcessBuilder(
            commands,
        )
        builder.directory(output.parentFile)
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
            listOf(arFile.path, "-x", archive.path) + args,
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
