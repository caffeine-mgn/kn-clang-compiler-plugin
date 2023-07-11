package pw.binom.kotlin.clang

import java.io.File

interface Linker {
    fun static(objectFiles: List<File>, output: File)
    fun extract(archive: File, outputDirectory: File)
}
