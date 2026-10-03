package pw.binom.kotlin.clang

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.konan.BaseKonanVersion
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream

interface KonanVersion {
    fun getTargetInfo(target: KonanTarget): TargetInfo =
        findTargetInfo(target) ?: throw RuntimeException("Not supported $target")

    fun findTargetInfo(target: KonanTarget): TargetInfo?
    fun sysRoot(target: KonanTarget): String?
    fun gccToolchain(target: KonanTarget): String?
    fun findCppCompiler(target: KonanTarget): CppCompiler?
    fun getCppCompiler(target: KonanTarget) =
        findCppCompiler(target) ?: throw RuntimeException("Not supported $target")

    fun findLinked(target: KonanTarget): Linker?
    fun getLinked(target: KonanTarget) =
        findLinked(target) ?: throw RuntimeException("Not supported $target")

    val HOST_LLVM_BIN_FOLDER: File

    companion object {
        fun findVersion(version: KonanVersionNumber): KonanVersion = BaseKonanVersion(version)
        fun getVersion(version: KonanVersionNumber): KonanVersion = findVersion(version)
    }
}

object Konan {
    private val TMP_SOURCE_FILE by lazy {
        val file = File.createTempFile("helloworld", ".kt")
        file.writeText("fun main()=println()")
        file.deleteOnExit()
        file
    }

    private fun prebuildDir(version: KonanVersionNumber) =
        KONAN_USER_DIR.resolve(PREBUILD_KONAN_DIR_NAME(version = version))

    /**
     * Guards [checkKonanInstalled] across concurrently-running Gradle workers so
     * the archive can't be downloaded/unpacked twice into the same directory.
     */
    private val installLock = Any()

    fun KONAN_EXE_PATH(version: KonanVersionNumber): File {
        val binFolder = prebuildDir(version).resolve("bin")
        return if (HostManager.hostIsMingw) {
            binFolder.resolve("kotlinc-native.bat")
        } else {
            binFolder.resolve("kotlinc-native")
        }
    }

    fun checkKonanInstalled(version: KonanVersionNumber) {
        if (prebuildDir(version).resolve("konan/konan.properties").isFile) {
            return
        }
        synchronized(installLock) {
            if (prebuildDir(version).resolve("konan/konan.properties").isFile) {
                return
            }
            doCheckKonanInstalled(version)
        }
    }

    private fun doCheckKonanInstalled(version: KonanVersionNumber) {
        val dir = prebuildDir(version)
        // `konan/konan.properties` is the marker a complete install leaves behind
        // (both this plugin and KGP write it). A bare directory only means the
        // archive's top-level entry was created before unpacking failed, so
        // treating it as "installed" would poison every subsequent run.
        if (dir.exists() && !dir.deleteRecursively()) {
            throw RuntimeException("Can't clean up incomplete Kotlin/Native install at $dir")
        }
        println("Please wait while Kotlin/Native compiler $version is being installed.")
        val arch = System.getProperty("os.arch")
        val prebuild = "-prebuilt"
        val url = when {
            HostManager.hostIsLinux -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native$prebuild-linux-x86_64-$version.tar.gz"
            HostManager.hostIsMac && arch == "aarch64" -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native$prebuild-macos-aarch64-$version.tar.gz"
            HostManager.hostIsMac -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native$prebuild-macos-x86_64-$version.tar.gz"
            HostManager.hostIsMingw -> "https://github.com/JetBrains/kotlin/releases/download/v$version/kotlin-native$prebuild-windows-x86_64-$version.zip"
            else -> throw RuntimeException("Unsupported host ${HostManager.hostOs()}:${HostManager.hostArch()}")
        }
        println("Getting Konan from Url \"$url\"")
        // Unpack into a scratch dir first: the K/N archives wrap the payload in a
        // single top-level directory named after the distribution, so unpacking
        // straight into `prebuildDir` would nest it one level too deep and leave
        // `prebuildDir/konan/konan.properties` (what BaseKonanVersion reads) missing.
        val tmp = File.createTempFile("kn-clang-konan", "").apply { delete(); mkdirs() }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            if (connection.responseCode != 200) {
                throw RuntimeException("Can't download konan from \"$url\". Invalid response code: ${connection.responseCode}")
            }
            try {
                when {
                    url.endsWith(".tar.gz") -> unpackTargz(connection.inputStream, tmp)
                    url.endsWith(".zip") -> unpackZip(connection.inputStream, tmp)
                    else -> throw RuntimeException("Unsupported archive \"$url\"")
                }
                installKonanFrom(tmp = tmp, dir = dir)
            } catch (e: Throwable) {
                throw RuntimeException("Can't unpack konan", e)
            }
        } finally {
            tmp.deleteRecursively()
            connection.disconnect()
        }
    }

    /**
     * Moves an unpacked K/N distribution from the scratch [tmp] into [dir],
     * stripping the archive's single top-level directory if present (the
     * `kotlin-native-prebuilt-*` archives always wrap their payload in one).
     */
    internal fun installKonanFrom(tmp: File, dir: File) {
        // Only strip a single wrapper dir that is actually the distribution
        // (`kotlin-native-prebuilt-*`); a lone unrelated dir must be preserved.
        val root = tmp.listFiles()?.singleOrNull()
            ?.takeIf { it.isDirectory && it.name.startsWith("kotlin-native-") }
            ?: tmp
        dir.parentFile?.mkdirs()
        if (!root.renameTo(dir) && !root.copyRecursively(dir, overwrite = true)) {
            throw RuntimeException("Can't move Kotlin/Native into $dir")
        }
    }

    fun checkSysrootInstalled(version: KonanVersionNumber, target: KonanTarget) {
        checkKonanInstalled(version = version)
        val info = KonanVersion.getVersion(version).findTargetInfo(target)
            ?: throw RuntimeException("Target \"${target.name}\" not supported")
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
        println("Executing $startArg")
        println("in ${TMP_SOURCE_FILE.parentFile}")
        val pb = ProcessBuilder(*startArg.toTypedArray())
        pb.directory(TMP_SOURCE_FILE.parentFile)
        pb.environment().putAll(System.getenv())
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT)
        val process = pb.start()
        StreamGobblerAppendable(process.inputStream, dest = System.out, appendNewLine = false).start()
        StreamGobblerAppendable(process.errorStream, dest = System.err, appendNewLine = false).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw RuntimeException("Can't execute konan")
        }
    }

    internal fun tarGzOfFiles(vararg names: String): InputStream {
        val bytes = java.io.ByteArrayOutputStream()
        org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream(bytes).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                names.forEach { name ->
                    val entry = TarArchiveEntry(name)
                    entry.size = 1
                    entry.mode = if (name.substringAfterLast('/').startsWith("kotlinc-native") || name.endsWith(".sh")) {
                        0b111_101_101
                    } else {
                        0b110_100_100
                    }
                    tar.putArchiveEntry(entry)
                    tar.write('x'.code)
                    tar.closeArchiveEntry()
                }
            }
        }
        return bytes.toByteArray().inputStream()
    }

    fun unpackTargz(stream: InputStream, dest: File) {
        GzipCompressorInputStream(stream).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val entryFile = dest.resolve(entry.name)
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
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
                        applyEntryMode(entryFile, (entry as TarArchiveEntry).mode)
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }

    fun unpackZip(stream: InputStream, dest: File) {
        ZipArchiveInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryFile = dest.resolve(entry.name)
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
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
                    applyEntryMode(entryFile, (entry as ZipArchiveEntry).unixMode)
                }
                entry = zip.nextEntry
            }
        }
    }

    /**
     * Restores the POSIX permission bits declared by an archive entry.
     * Without this the Kotlin/Native toolchain lands as `0644` and
     * `kotlinc-native`, `konanc`, `run_konan`, the clang binaries, … are not
     * executable — `checkSysrootInstalled` then fails with "Can't execute konan".
     */
    private fun applyEntryMode(file: File, mode: Int) {
        if (mode == 0) return
        val perms = buildSet {
            if (mode and 0b100_000_000 != 0) add(PosixFilePermission.OWNER_READ)
            if (mode and 0b010_000_000 != 0) add(PosixFilePermission.OWNER_WRITE)
            if (mode and 0b001_000_000 != 0) add(PosixFilePermission.OWNER_EXECUTE)
            if (mode and 0b000_100_000 != 0) add(PosixFilePermission.GROUP_READ)
            if (mode and 0b000_010_000 != 0) add(PosixFilePermission.GROUP_WRITE)
            if (mode and 0b000_001_000 != 0) add(PosixFilePermission.GROUP_EXECUTE)
            if (mode and 0b000_000_100 != 0) add(PosixFilePermission.OTHERS_READ)
            if (mode and 0b000_000_010 != 0) add(PosixFilePermission.OTHERS_WRITE)
            if (mode and 0b000_000_001 != 0) add(PosixFilePermission.OTHERS_EXECUTE)
        }
        runCatching { Files.setPosixFilePermissions(file.toPath(), perms) }
    }
}
