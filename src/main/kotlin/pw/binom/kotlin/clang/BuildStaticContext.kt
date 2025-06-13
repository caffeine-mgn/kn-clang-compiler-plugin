package pw.binom.kotlin.clang

import java.io.File

interface BuildStaticContext {
    fun file(file: File): BuildStaticContext
    fun files(wildcard: String, directory: File): BuildStaticContext
    fun files(files:Iterable<File>){
        files.forEach(::file)
    }
    fun files(files:Sequence<File>){
        files.forEach(::file)
    }
}