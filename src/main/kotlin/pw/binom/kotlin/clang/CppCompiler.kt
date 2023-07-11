package pw.binom.kotlin.clang

import org.gradle.api.logging.Logger
import java.io.File

interface CppCompiler {
    sealed interface CompileResult {
        object OK : CompileResult {
            override val isOk: Boolean
                get() = true
        }

        class Error(val exitCode: Int, val stdout: String, val stderr: String) : CompileResult {
            override val isOk: Boolean
                get() = false
        }

        val isOk: Boolean
    }

    fun compile(inputFiles: File, outputFile: File, logger: Logger): CompileResult
}
