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

    private class BuildStaticContextImpl : BuildStaticContext {
        val values = ArrayList<String>()
        override fun file(file: File): BuildStaticContext {
            values += file.absolutePath
            return this
        }

        override fun files(wildcard: String, directory: File): BuildStaticContext {
            values += "${directory.absolutePath}${File.separator}$wildcard"
            return this
        }

    }

    fun runAndWait(args: List<String>, directory: File? = null) {
        val builder = ProcessBuilder(
            args
        )
        if (directory != null) {
            builder.directory(directory)
            directory.mkdirs()
        }
//        builder.environment()["PATH"] = "${arFile.parentFile.path.replace("\\", "\\\\")};${System.getenv("PATH")}"
        val process = try {
            builder.start()
        } catch (e: Throwable) {
            throw GradleScriptException("Can't run ${args.joinToString(" ")}", e)
        }
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

    override fun static(output: File, config: BuildStaticContext.() -> Unit) {
        val ctx = BuildStaticContextImpl()
        config(ctx)
        output.delete()
        val baseArgs = listOf(arFile.path, "rc", output.path)
        val baseLength = baseArgs.sumOf { it.length }

        val args2 = ArrayList<String>()
        var argsLength = 0
        ctx.values.forEach {
            if (argsLength + baseLength >= 1000) {
                runAndWait(baseArgs + args2)
                args2.clear()
                argsLength = 0
            }
            args2 += it
            argsLength += it.length
        }
        if (args2.isNotEmpty()) {
            runAndWait(baseArgs + args2)
        }
        return

        val args = listOf(arFile.path, "rc", output.path) + ctx.values + args
        println("Args:")
        args.forEach {
            println("-->$it")
        }
        runAndWait(args = args)
//        val builder = ProcessBuilder(
//            args
//        )
//        builder.directory(output.parentFile)
//        output.parentFile.mkdirs()
////        builder.environment()["PATH"] = "${arFile.parentFile.path.replace("\\", "\\\\")};${System.getenv("PATH")}"
//        val process = try {
//            builder.start()
//        } catch (e: Throwable) {
//            throw GradleScriptException("Can't run ${args.joinToString(" ")}", e)
//        }
//        StreamGobblerAppendable(process.inputStream, System.out, false).start()
//        StreamGobblerAppendable(process.errorStream, System.err, false).start()
//        process.waitFor()
//        if (process.exitValue() != 0) {
//            throw GradleScriptException(
//                "Can't execute link static library",
//                RuntimeException("Can't link: Linked returns ${process.exitValue()}"),
//            )
//        }
    }

    override fun static(objectFiles: List<File>, output: File) {
        static(output = output) {
            objectFiles.forEach {
                file(it)
            }
        }
        return
        val args = listOf(arFile.path, "rc", output.path) + objectFiles.map { it.path } + args
        val builder = ProcessBuilder(
            args
        )
        builder.directory(output.parentFile)
        output.parentFile.mkdirs()
//        builder.environment()["PATH"] = "${arFile.parentFile.path.replace("\\", "\\\\")};${System.getenv("PATH")}"
        val process = try {
            builder.start()
        } catch (e: Throwable) {
            throw GradleScriptException("Can't run ${args.joinToString(" ")}", e)
        }
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
