package pw.binom.kotlin.clang

import java.io.BufferedReader
import java.io.IOException

import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Appendable

abstract class AbstractStreamGobbler constructor(var stream: InputStream) : Thread() {
    protected abstract fun append(line: String)
    override fun run() {
        try {
            val isr = InputStreamReader(stream)
            val br = BufferedReader(isr)
            var line: String?
            while (br.readLine().also { line = it } != null)
                append("$line\n")
        } catch (ioe: IOException) {
            ioe.printStackTrace()
        }
    }
}

class StreamGobbler constructor(stream: InputStream) : AbstractStreamGobbler(stream) {
    val out = StringBuilder()
    override fun append(line: String) {
        out.appendLine(line)
    }
}

//class StreamGobblerStdout constructor(stream: InputStream) : AbstractStreamGobbler(stream) {
//    override fun append(line: String) {
//        println(line)
//    }
//}
//
//class StreamGobblerStderr constructor(stream: InputStream) : AbstractStreamGobbler(stream) {
//    override fun append(line: String) {
//        System.err.println(line)
//    }
//}

class StreamGobblerAppendable constructor(stream: InputStream, val dest: Appendable, val appendNewLine: Boolean) :
    AbstractStreamGobbler(stream) {
    override fun append(line: String) {
        dest.append(line)
        if (appendNewLine) {
            dest.appendLine()
        }
    }
}