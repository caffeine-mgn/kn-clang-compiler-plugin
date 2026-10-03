package pw.binom.kotlin.clang

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File

class KonanUnpackTest {

    private fun tarGzOf(vararg entries: Pair<String, Boolean>): ByteArray {
        val bytes = ByteArrayOutputStream()
        GzipCompressorOutputStream(bytes).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                entries.forEach { (name, isDir) ->
                    val entry = TarArchiveEntry(name + if (isDir) "/" else "")
                    if (isDir) {
                        entry.mode = 0b111_111_101
                    } else {
                        entry.size = 1
                    }
                    tar.putArchiveEntry(entry)
                    if (!isDir) {
                        tar.write('x'.code)
                    }
                    tar.closeArchiveEntry()
                }
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `unpackTargz creates parent directories for nested files`() {
        val root = File.createTempFile("kn-clang-unpack", "").apply { delete(); mkdirs() }
        try {
            val nested = "kotlin-native-prebuilt-linux-x86_64-2.4.20/konan/konan.properties"
            Konan.unpackTargz(
                tarGzOf(nested to false).inputStream(),
                root,
            )
            assertTrue(root.resolve(nested).isFile, "Nested file was not created: $nested")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `installKonanFrom strips the single top-level directory`() {
        val tmp = File.createTempFile("kn-clang-tmp", "").apply { delete(); mkdirs() }
        val dir = File.createTempFile("kn-clang-dest", "").apply { delete() }
        try {
            val marker = tmp.resolve("kotlin-native-prebuilt-linux-x86_64-2.4.20/konan/konan.properties")
            marker.parentFile.mkdirs()
            marker.writeText("ok")

            Konan.installKonanFrom(tmp = tmp, dir = dir)

            assertTrue(dir.resolve("konan/konan.properties").isFile, "Top-level dir was not stripped")
            assertFalse(
                dir.resolve("kotlin-native-prebuilt-linux-x86_64-2.4.20").exists(),
                "Nested distribution dir is still present",
            )
        } finally {
            tmp.deleteRecursively()
            dir.deleteRecursively()
        }
    }

    @Test
    fun `installKonanFrom keeps a flat layout when there is no wrapper dir`() {
        val tmp = File.createTempFile("kn-clang-tmp", "").apply { delete(); mkdirs() }
        val dir = File.createTempFile("kn-clang-dest", "").apply { delete() }
        try {
            tmp.resolve("konan/konan.properties").apply { parentFile.mkdirs() }.writeText("ok")

            Konan.installKonanFrom(tmp = tmp, dir = dir)

            assertTrue(dir.resolve("konan/konan.properties").isFile, "Flat layout was not preserved")
        } finally {
            tmp.deleteRecursively()
            dir.deleteRecursively()
        }
    }
}
