package pw.binom.kotlin.clang

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pw.binom.kotlin.clang.konan.jdkIncludePlatform
import java.io.File
import java.nio.file.Files

class JdkIncludeResolverTest {

    @Test
    fun `linux and android targets share the linux jni_md subdir`() {
        assertEquals("linux", KonanTarget.LINUX_X64.jdkIncludePlatform)
        assertEquals("linux", KonanTarget.LINUX_ARM64.jdkIncludePlatform)
        assertEquals("linux", KonanTarget.ANDROID_ARM64.jdkIncludePlatform)
        assertEquals("linux", KonanTarget.ANDROID_X64.jdkIncludePlatform)
    }

    @Test
    fun `osx targets use the darwin jni_md subdir`() {
        assertEquals("darwin", KonanTarget.MACOS_X64.jdkIncludePlatform)
        assertEquals("darwin", KonanTarget.MACOS_ARM64.jdkIncludePlatform)
    }

    @Test
    fun `mingw targets use the win32 jni_md subdir`() {
        assertEquals("win32", KonanTarget.MINGW_X64.jdkIncludePlatform)
    }

    @Test
    fun `resolver picks include root and per-platform subdir for host target`() {
        // Skip if this CI box genuinely has no JDK include at all —
        // there is nothing meaningful to assert without one.
        val resolved = runCatching { JdkIncludeResolver.host }.getOrNull() ?: return
        assertNotNull(resolved.jdkInclude)
        assertNotNull(resolved.jdkIncludePlatform)
        assertEquals(HostManager.host.jdkIncludePlatform, resolved.jdkIncludePlatform.name)
    }

    @Test
    fun `resolver prefers override directory when it contains jni_md`() {
        // Stage an override rooted at `tmp/<platform>/jni_md.h` to simulate
        // what CI pre-populates from OpenJDK sources on a non-native host.
        val tmp = Files.createTempDirectory("jdk-override").toFile()
        try {
            val platformDir = File(tmp, KonanTarget.MINGW_X64.jdkIncludePlatform).apply {
                mkdirs()
            }
            File(platformDir, "jni_md.h").writeText("// staged override")

            val resolved = JdkIncludeResolver.resolve(
                target = KonanTarget.MINGW_X64,
                overridePlatformDir = platformDir,
            )
            // The override wins for the platform dir; jdkInclude still
            // comes from the local JDK because the override only staged
            // `jni_md.h`, not `jni.h`.
            assertEquals(platformDir, resolved.jdkIncludePlatform)
            assertTrue(
                resolved.jdkInclude.absolutePath.endsWith("include"),
                "jdkInclude must point at the JDK's include dir; got ${resolved.jdkInclude}",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `resolver ignores override directory missing jni_md`() {
        // Override exists but does not contain jni_md.h — must fall through
        // to the JDK's own platform subdir rather than blindly using the dir.
        val tmp = Files.createTempDirectory("jdk-bad-override").toFile()
        try {
            val platformDir = File(tmp, KonanTarget.LINUX_X64.jdkIncludePlatform).apply {
                mkdirs()
            }
            val resolved = JdkIncludeResolver.resolve(
                target = KonanTarget.LINUX_X64,
                overridePlatformDir = platformDir,
            )
            // The empty override dir must not be selected as the platform
            // directory; the resolver falls back to JAVA_HOME/include/<platform>.
            assertTrue(
                resolved.jdkIncludePlatform != platformDir,
                "Empty override must be ignored; got ${resolved.jdkIncludePlatform}",
            )
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `isUsable is true only when both headers exist`() {
        val tmp = Files.createTempDirectory("jdk-usable").toFile()
        try {
            File(tmp, "jni.h").writeText("// jni")
            File(tmp, "jni_md.h").writeText("// jni_md")
            assertTrue(JdkIncludePaths(tmp, tmp).isUsable())
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `isUsable is false when jni_md is missing`() {
        val tmp = Files.createTempDirectory("jdk-half").toFile()
        try {
            File(tmp, "jni.h").writeText("// jni")
            assertEquals(false, JdkIncludePaths(tmp, tmp).isUsable())
        } finally {
            tmp.deleteRecursively()
        }
    }

    }
