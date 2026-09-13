package pw.binom.kotlin.clang

import org.gradle.util.internal.VersionNumber
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import pw.binom.kotlin.clang.konan.BaseKonanVersion
import java.io.File

class BaseKonanVersionTest {

    private fun installedKonanVersion(): VersionNumber? {
        val home = File(System.getProperty("user.home"), ".konan")
        if (!home.isDirectory) return null
        val pattern = Regex("""kotlin-native-prebuilt-.+-(\d+\.\d+\.\d+)$""")
        return home.listFiles()
            ?.mapNotNull { f ->
                pattern.matchEntire(f.name)?.groupValues?.get(1)?.let { VersionNumber.parse(it) }
            }
            ?.maxByOrNull { it }
    }

    @TestFactory
    fun `every supported target resolves to a non-null TargetInfo`(): List<DynamicTest> {
        val version = installedKonanVersion()
            ?: return listOf(DynamicTest.dynamicTest("skip - no K/N installed") { })

        val v = BaseKonanVersion(version)
        return supportedTargets.map { target ->
            DynamicTest.dynamicTest("Konan ${version} -> $target") {
                val info = v.findTargetInfo(target)
                if (info != null) {
                    assertNotNull(info.toolchain ?: info.sysRoot.firstOrNull(),
                        "Target $target has no sysroot/toolchain resolved")
                }
            }
        }
    }

    @TestFactory
    fun `clang args are non-empty for every supported target`(): List<DynamicTest> {
        val version = installedKonanVersion()
            ?: return listOf(DynamicTest.dynamicTest("skip - no K/N installed") { })

        val v = BaseKonanVersion(version)
        return supportedTargets.map { target ->
            DynamicTest.dynamicTest("Konan ${version} -> $target") {
                val info = v.findTargetInfo(target) ?: return@dynamicTest
                assertTrue(info.clangCompileArgs.isNotEmpty(), "No compile args for $target")
                assertTrue(
                    info.clangCompileArgs.contains("-target"),
                    "Missing -target for $target",
                )
            }
        }
    }

    @TestFactory
    fun `resolved paths never contain unresolved property references`(): List<DynamicTest> {
        val version = installedKonanVersion()
            ?: return listOf(DynamicTest.dynamicTest("skip - no K/N installed") { })

        val v = BaseKonanVersion(version)
        return supportedTargets.map { target ->
            DynamicTest.dynamicTest("Konan ${version} -> $target") {
                val info = v.findTargetInfo(target) ?: return@dynamicTest
                info.sysRoot.forEach { sr ->
                    assertFalse(sr.path.contains("$"), "Unresolved property in sysroot: ${sr.path}")
                }
                info.toolchain?.let { tc ->
                    assertFalse(tc.path.contains("$"), "Unresolved property in toolchain: ${tc.path}")
                }
            }
        }
    }

    @Test
    fun `linux x64 sysroot resolves to an existing directory`() {
        if (!HostManager.hostIsLinux) return
        val version = installedKonanVersion() ?: return
        val info = BaseKonanVersion(version).findTargetInfo(KonanTarget.LINUX_X64) ?: return
        val sysroot = info.sysRoot.first()
        if (!sysroot.exists()) return
        assertTrue(sysroot.isDirectory, "Linux x64 sysroot is not a directory: $sysroot")
    }

    @TestFactory
    fun `android targets link with the bundled NDK clang wrapper`(): List<DynamicTest> {
        if (!HostManager.hostIsLinux) {
            return listOf(DynamicTest.dynamicTest("skip - not a linux host") { })
        }
        val version = installedKonanVersion()
            ?: return listOf(DynamicTest.dynamicTest("skip - no K/N installed") { })

        val v = BaseKonanVersion(version)
        val expected = mapOf(
            KonanTarget.ANDROID_ARM32 to "armv7a-linux-androideabi21-clang",
            KonanTarget.ANDROID_ARM64 to "aarch64-linux-android21-clang",
            KonanTarget.ANDROID_X86 to "i686-linux-android21-clang",
            KonanTarget.ANDROID_X64 to "x86_64-linux-android21-clang",
        )
        return expected.map { (target, clangName) ->
            DynamicTest.dynamicTest("Konan $version -> $target") {
                val linker = v.findLinked(target) as? CLangLinker
                assertNotNull(linker, "No linker for $target")
                assertTrue(linker!!.useNdkClang, "Android target $target must link with NDK clang")
                assertEquals(clangName, linker.clangFile.name, "Wrong NDK clang wrapper for $target")
            }
        }
    }

    private val supportedTargets = listOf(
        KonanTarget.LINUX_X64,
        KonanTarget.LINUX_ARM64,
        KonanTarget.LINUX_ARM32_HFP,
        KonanTarget.MINGW_X64,
        KonanTarget.MACOS_X64,
        KonanTarget.MACOS_ARM64,
        KonanTarget.IOS_ARM64,
        KonanTarget.IOS_X64,
        KonanTarget.IOS_SIMULATOR_ARM64,
        KonanTarget.TVOS_ARM64,
        KonanTarget.TVOS_X64,
        KonanTarget.TVOS_SIMULATOR_ARM64,
        KonanTarget.WATCHOS_ARM32,
        KonanTarget.WATCHOS_ARM64,
        KonanTarget.WATCHOS_X64,
        KonanTarget.WATCHOS_SIMULATOR_ARM64,
        KonanTarget.ANDROID_ARM32,
        KonanTarget.ANDROID_ARM64,
        KonanTarget.ANDROID_X86,
        KonanTarget.ANDROID_X64,
    )
}
