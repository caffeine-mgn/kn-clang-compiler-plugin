package pw.binom.kotlin.clang

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.gradle.api.GradleException
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.konan.V1_8_0
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

interface KonanVersion {
    fun getTargetInfo(target: KonanTarget): TargetInfo =
        findTargetInfo(target) ?: throw GradleException("Not supported $target")

    fun findTargetInfo(target: KonanTarget): TargetInfo?
    fun findCppCompiler(target: KonanTarget): CppCompiler?
    fun getCppCompiler(target: KonanTarget) =
        findCppCompiler(target) ?: throw GradleException("Not supported $target")

    fun findLinked(target: KonanTarget): Linker?
    fun getLinked(target: KonanTarget) =
        findLinked(target) ?: throw GradleException("Not supported $target")

    companion object {
        private val versions = mapOf(
            "1.8.0" to V1_8_0,
            "1.8.10" to V1_8_0,
            "1.8.20" to V1_8_0,
            "1.8.21" to V1_8_0,
            "1.9.20" to V1_8_0,
            "1.9.21" to V1_8_0,
            "1.9.22" to V1_8_0,
        )

        fun findVersion(version: String) = versions[version]
        fun getVersion(version: String) =
            findVersion(version) ?: throw GradleException("CLang for konan \"$version\" not supported")
    }
}

object Konan {
    private val TMP_SOURCE_FILE by lazy {
        val file = File.createTempFile("helloworld", ".kt")
        file.writeText("fun main()=println()")
        file.deleteOnExit()
        file
    }

    private fun prebuildDir(version: String) = KONAN_USER_DIR.resolve(PREBUILD_KONAN_DIR_NAME(version = version))

    fun KONAN_EXE_PATH(version: String): File {
        val binFolder = prebuildDir(version).resolve("bin")
        return if (HostManager.hostIsMingw) {
            binFolder.resolve("kotlinc-native.bat")
        } else {
            binFolder.resolve("kotlinc-native")
        }
    }

    fun checkKonanInstalled(version: String) {
        if (prebuildDir(version).isDirectory) {
            return
        }
        println("Please wait while Kotlin/Native compiler $version is being installed.")
        val arch = System.getProperty("os.arch")
        val url = when {
            HostManager.hostIsLinux -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native-linux-x86_64-$version.tar.gz"
            HostManager.hostIsMac && arch == "aarch64" -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native-macos-aarch64-$version.tar.gz"
            HostManager.hostIsMac -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native-macos-x86_64-$version.tar.gz"
            HostManager.hostIsMingw -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native-windows-x86_64-$version.zip"
            else -> throw RuntimeException("Unsupported host ${HostManager.hostOs()}:${HostManager.hostArch()}")
        }
        println("Getting Konan from Url \"$url\"")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            if (connection.responseCode != 200) {
                throw RuntimeException("Can't download konan from \"$url\". Invalid response code: ${connection.responseCode}")
            }
            try {
                when {
                    url.endsWith(".tar.gz") -> unpackTargz(connection.inputStream, prebuildDir(version))
                    url.endsWith(".zip") -> unpackZip(connection.inputStream, prebuildDir(version))
                    else -> throw RuntimeException("Unsupported archive \"$url\"")
                }
            } catch (e: Throwable) {
                throw RuntimeException("Can't unpack konan", e)
            }
        } finally {
            connection.disconnect()
        }
    }

    fun checkSysrootInstalled(version: String, target: KonanTarget) {
        checkKonanInstalled(version = version)
        val info = targetInfoMap[target] ?: throw RuntimeException("Target \"${target.name}\" not supported")
        if (info.sysRoot.all { it.isDirectory }) {
            return
        }
        println("Please wait while Sysroot ${target.name} is being installed. Exists: ${info.sysRoot.associateWith { it.exists() }}")
        val args = listOf("-target", target.name, TMP_SOURCE_FILE.absolutePath)
        val startArg = when {
            HostManager.hostIsLinux || HostManager.hostIsMac -> listOf(
                "bash",
                "-c",
                "'${KONAN_EXE_PATH(version).absolutePath}' ${args.map { "'$it'" }.joinToString(" ")}",
            )

            HostManager.hostIsMingw -> listOf("cmd", "/c", KONAN_EXE_PATH(version).absolutePath) + args
            else -> throw RuntimeException("Current platform is not supported")
        }
//        val konancCmd = (startArg + args).toTypedArray()
        println("Executing $startArg")
        println("in ${TMP_SOURCE_FILE.parentFile}")
        val pb = ProcessBuilder(*startArg.toTypedArray())
        pb.directory(TMP_SOURCE_FILE.parentFile)
        pb.environment().putAll(System.getenv())
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        val process = pb.start()
        StreamGobblerAppendable(process.inputStream, dest = System.out, appendNewLine = false).start()
        StreamGobblerAppendable(process.errorStream, dest = System.err, appendNewLine = false).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException("Can't execute konan")
        }
    }

    fun unpackTargz(stream: InputStream, dest: File) {
        GzipCompressorInputStream(stream).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val entryFile = dest.resolve(entry.name)
                    if (entry.isDirectory) {
                        if (!entryFile.mkdirs()) {
                            throw RuntimeException("Failed to create $entryFile")
                        }
                    } else {
                        FileOutputStream(entryFile).use { fos ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val len = tar.read(buffer)
                                if (len <= 0) {
                                    break
                                }
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    fun unpackZip(stream: InputStream, dest: File) {
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryFile = dest.resolve(entry.name)
                if (entry.isDirectory) {
                    if (!entryFile.mkdirs()) {
                        throw RuntimeException("Failed to create $entryFile")
                    }
                } else {
                    FileOutputStream(entryFile).use { fos ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val len = zip.read(buffer)
                            if (len <= 0) {
                                break
                            }
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                entry = zip.nextEntry
            }
        }
    }
}
