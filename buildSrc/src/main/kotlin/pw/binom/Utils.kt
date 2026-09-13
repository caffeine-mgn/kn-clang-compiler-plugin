package pw.binom

import java.io.ByteArrayOutputStream

fun getGitBranch(): String {
    val stdout = ByteArrayOutputStream()
    val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
        .redirectErrorStream(false)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    process.inputStream.use { stdout.write(it.readBytes()) }
    process.waitFor()
    return stdout.toString().trim()
}