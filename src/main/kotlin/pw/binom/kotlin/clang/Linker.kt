package pw.binom.kotlin.clang

import java.io.File

interface Linker {
    fun static(output: File, config: BuildStaticContext.() -> Unit)
    fun static(objectFiles: List<File>, output: File)
    fun dynamic(objectFiles: List<File>, output: File, linkArgs: List<String>)
    fun extract(archive: File, outputDirectory: File)
}
