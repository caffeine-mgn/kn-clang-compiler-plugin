package pw.binom.knclang.cli

import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.CLang
import pw.binom.kotlin.clang.CLangLinker
import pw.binom.kotlin.clang.Konan
import pw.binom.kotlin.clang.KonanVersion
import pw.binom.kotlin.clang.KonanVersionNumber
import pw.binom.kotlin.clang.konan.clangTarget

/**
 * Entry point for the standalone `kn-clang-cli` command.
 *
 * Usage:
 *   kn-clang-cli <target> [--version <kn-version>] [--format shell|json] [--download]
 *
 * Resolves the Clang toolchain for a Kotlin/Native target via the same code path the
 * Gradle plugin uses, and prints the result in a shell- or JSON-friendly form so it can
 * be sourced into a build script (CMake/Make/autoconf) that drives onnxruntime,
 * LiteRT, or other third-party C/C++ libraries.
 *
 * This is the JVM-only build (sessions 0.0.9). The KMP-native build with three host
 * targets (linuxX64 / macosArm64 / mingwX64) lands in 0.1.0.
 */
fun main(args: Array<String>) {
    val parser = ArgParser(args)
    val targetName = parser.positional.getOrNull(0)
        ?: error("Usage: kn-clang-cli <target> [--version V] [--format shell|json] [--download]")
    val target = KonanTarget.predefinedTargets.values
        .firstOrNull { it.name.equals(targetName, ignoreCase = true) }
        ?: error("Unknown target: $targetName")
    val version = KonanVersionNumber.parse(parser.version ?: "2.4.20")
    val format = Format.from(parser.format)

    if (parser.download) {
        Konan.checkSysrootInstalled(version, target)
    }

    when (format) {
        Format.SHELL -> printShell(version, target)
        Format.JSON -> printJson(version, target)
    }
}

private fun printShell(version: KonanVersionNumber, target: KonanTarget) {
    val konan = KonanVersion.getVersion(version)
    val info = konan.findTargetInfo(target) ?: error("Target $target not supported by K/N $version")
    val link = konan.getLinked(target) as CLangLinker
    val clang = (konan.findCppCompiler(target) as CLang).clangFile
    val cxx = deriveCxxCompiler(clang)
    val archiver = link.arFile
    val linker = link.ldFile
    val sysRoot = info.sysRoot.first()
    val triple = target.clangTarget
    val cflags = info.clangCompileArgs.joinToString(" ")

    println("KN_CLANG_TARGET=$target")
    println("KN_CLANG_TRIPLE=$triple")
    println("KN_CLANG_COMPILER=${clang.absolutePath}")
    println("KN_CLANG_CXX_COMPILER=${cxx.absolutePath}")
    println("KN_CLANG_ARCHIVER=${archiver.absolutePath}")
    println("KN_CLANG_LINKER=${linker.absolutePath}")
    println("KN_CLANG_SYSROOT=${sysRoot.absolutePath}")
    println("KN_CLANG_TOOLCHAIN=${info.toolchain?.absolutePath ?: ""}")
    println("KN_CLANG_CFLAGS=$cflags")
    println("KN_CLANG_KONAN_VERSION=$version")
}

private fun printJson(version: KonanVersionNumber, target: KonanTarget) {
    val konan = KonanVersion.getVersion(version)
    val info = konan.findTargetInfo(target) ?: error("Target $target not supported by K/N $version")
    val link = konan.getLinked(target) as CLangLinker
    val clang = (konan.findCppCompiler(target) as CLang).clangFile
    val cxx = deriveCxxCompiler(clang)

    val map = linkedMapOf<String, Any?>(
        "target" to target.name,
        "triple" to target.clangTarget,
        "compiler" to clang.absolutePath,
        "cxxCompiler" to cxx.absolutePath,
        "archiver" to link.arFile.absolutePath,
        "linker" to link.ldFile.absolutePath,
        "sysRoot" to info.sysRoot.first().absolutePath,
        "toolchain" to info.toolchain?.absolutePath,
        "cFlags" to info.clangCompileArgs,
        "konanVersion" to version.raw,
    )
    println(Json.encode(map))
}

private fun deriveCxxCompiler(clang: java.io.File): java.io.File {
    val name = clang.name
    return if (name.endsWith(".exe")) {
        java.io.File(clang.parentFile, clang.nameWithoutExtension + "++" + ".exe")
    } else {
        java.io.File(clang.parentFile, name + "++")
    }
}

private enum class Format(val cliValue: String) {
    SHELL("shell"), JSON("json");

    companion object {
        fun from(s: String?): Format = when (s?.lowercase()) {
            null, "shell", "sh" -> SHELL
            "json" -> JSON
            else -> error("Unknown format: $s (use shell|json)")
        }
    }
}

private class ArgParser(args: Array<String>) {
    val positional = mutableListOf<String>()
    var version: String? = null
    var format: String? = null
    var download = false

    init {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a == "--version" || a == "-v" -> {
                    version = args.getOrNull(++i) ?: error("$a requires an argument")
                    i++
                }
                a == "--format" || a == "-f" -> {
                    format = args.getOrNull(++i) ?: error("$a requires an argument")
                    i++
                }
                a == "--download" || a == "-d" -> {
                    download = true; i++
                }
                a == "--help" || a == "-h" -> {
                    println(HELP); kotlin.system.exitProcess(0)
                }
                a.startsWith("-") -> error("Unknown option: $a")
                else -> { positional.add(a); i++ }
            }
        }
    }

    companion object {
        val HELP = """
            kn-clang-cli — print Clang toolchain info for a Kotlin/Native target

            Usage:
              kn-clang-cli <target> [options]

            Options:
              -v, --version V     Kotlin/Native version (default: 2.4.20)
              -f, --format FMT    Output format: shell (default) | json
              -d, --download      Ensure the sysroot is downloaded before resolving
              -h, --help          Show this help

            Examples:
              kn-clang-cli android_x86
              eval "$(kn-clang-cli android_arm64 --download --format shell)"
        """.trimIndent()
    }
}

private object LoggerBridge : pw.binom.kotlin.clang.Logger {
    override fun info(message: String) = println(message)
    override fun lifecycle(message: String) = println(message)
    override fun warn(message: String) = println("WARN: $message")
    override fun error(message: String) = System.err.println(message)
    override fun debug(message: String) {}
}

private object Json {
    fun encode(value: Any?): String = buildString { write(value) }
    private fun StringBuilder.write(v: Any?) {
        when (v) {
            null -> append("null")
            is Boolean -> append(v)
            is Number -> append(v)
            is String -> append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
            is Map<*, *> -> {
                append('{')
                var first = true
                for ((k, vv) in v) {
                    if (!first) append(',')
                    first = false
                    write(k.toString()); append(':'); write(vv)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                var first = true
                for (item in v) {
                    if (!first) append(',')
                    first = false
                    write(item)
                }
                append(']')
            }
            else -> write(v.toString())
        }
    }
}