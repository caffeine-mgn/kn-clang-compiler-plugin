package pw.binom.kotlin.clang

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

class CLang(val file: File, val target: KonanTarget, val args: List<String>) : CppCompiler {
    override fun compile(inputFiles: File, outputFile: File, logger: Logger): CppCompiler.CompileResult {
        val builder = ProcessBuilder(
            listOf(file.path) + args + listOf(inputFiles.path, "-o", outputFile.path),
        )

        val env = HashMap<String, String>()

        if (HostManager.hostIsMac && target == KonanTarget.MACOS_X64) {
            env["CPATH"] =
                "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include"
        }

//        val osPathSeparator = if (HostManager.hostIsMingw) {
//            ';'
//        } else {
//            ':'
//        }

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
        )
    }
}
