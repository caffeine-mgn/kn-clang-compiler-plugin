package pw.binom.kotlin.clang

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class CLang(val clangFile: File, val target: KonanTarget, val args: List<String>) : CppCompiler {
    override fun compile(
        inputFiles: File,
        outputFile: File,
        args: List<String>,
        logger: Logger,
    ): CppCompiler.CompileResult {
        val command = listOf(clangFile.path) + this.args + args + listOf(inputFiles.path, "-o", outputFile.path)
        val builder = ProcessBuilder(command)

        val env = HashMap<String, String>()

        if (HostManager.hostIsMac && target == KonanTarget.MACOS_X64) {
            env["CPATH"] =
                "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include"
        }

        env["PATH"] = "$HOST_LLVM_BIN_FOLDER${HostManager.pathSeparator}${System.getenv("PATH")}"

        builder.environment().putAll(env)
        val process = builder.start()

        val stdout = StreamGobbler(process.inputStream)
        val stderr = StreamGobbler(process.errorStream)
        stdout.start()
        stderr.start()
        process.waitFor()
        stdout.join()
        stderr.join()

        if (process.exitValue() == 0) {
            logger.lifecycle("Compile $outputFile: OK")
            return CppCompiler.CompileResult.OK
        }
        return CppCompiler.CompileResult.Error(
            exitCode = process.exitValue(),
            stdout = stdout.out.toString(),
            stderr = stderr.out.toString(),
            cmd = command,
        )
    }
}
