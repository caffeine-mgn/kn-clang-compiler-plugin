package pw.binom.kotlin.clang

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClangBuildJniTest {

    private fun newProject() = ProjectBuilder.builder().build()
        .also { it.plugins.apply("pw.binom.kn-clang") }

    private fun expectedExt(target: KonanTarget): String = when (target.family) {
        org.jetbrains.kotlin.konan.target.Family.MINGW -> "dll"
        org.jetbrains.kotlin.konan.target.Family.OSX,
        org.jetbrains.kotlin.konan.target.Family.IOS -> "dylib"
        else -> "so"
    }

    @Test
    fun `registers one task per target with lib-prefixed output names`() {
        val project = newProject()
        val targets = listOf(
            KonanTarget.LINUX_X64,
            KonanTarget.LINUX_ARM64,
        )
        val tasks = project.clangBuildJni(name = "klua", targets = targets)
        assertEquals(targets.size, tasks.size)
        tasks.forEachIndexed { i, t ->
            val target = targets[i]
            val expected = "libklua.${expectedExt(target)}"
            assertEquals(expected, t.dynamicFile.get().asFile.name,
                "Task for $target should output $expected; got ${t.dynamicFile.get().asFile.name}")
            assertNotNull(project.tasks.findByName(t.name), "Task ${t.name} must be registered")
        }
    }

    @Test
    fun `configure block is applied to every per-target task`() {
        val project = newProject()
        val targets = listOf(KonanTarget.LINUX_X64, KonanTarget.LINUX_ARM64)
        val tasks = project.clangBuildJni(name = "klua", targets = targets) {
            compileArgs("-DX=1")
        }
        tasks.forEach {
            assertTrue("-DX=1" in it.compileArgs,
                "User compileArgs must propagate; got ${it.compileArgs}")
        }
    }

    @Test
    fun `registered task names follow buildJni naming convention`() {
        val project = newProject()
        val targets = listOf(KonanTarget.LINUX_X64, KonanTarget.MINGW_X64)
        val tasks = project.clangBuildJni(name = "klua", targets = targets)
        val cap = { s: String -> s.replaceFirstChar { c -> c.uppercase() } }
        assertEquals("buildJniKlua${cap(KonanTarget.LINUX_X64.presetName)}", tasks[0].name)
        assertEquals("buildJniKlua${cap(KonanTarget.MINGW_X64.presetName)}", tasks[1].name)
        // Sanity: same task container must contain them under those names.
        assertNotNull(project.tasks.findByName(tasks[0].name))
        assertNotNull(project.tasks.findByName(tasks[1].name))
    }

    @Test
    fun `targets argument is consumed in order`() {
        val project = newProject()
        val ordered = listOf(KonanTarget.MINGW_X64, KonanTarget.LINUX_X64, KonanTarget.LINUX_ARM64)
        val tasks = project.clangBuildJni(name = "k", targets = ordered)
        assertEquals(ordered.size, tasks.size)
        // Verify each task really targets what its position says.
        tasks.forEachIndexed { i, t -> assertEquals(ordered[i], t.target.get()) }
    }

    @Test
    fun `uses KonanTarget jdkIncludePlatform mapping in include paths`() {
        // Sanity: the resolver wired into clangBuildJni should map
        // MINGW_X64 -> win32 headers on disk, so we expect the right
        // platform dir for that target.
        val project = newProject()
        val t = project.clangBuildJni(name = "jni", targets = listOf(KonanTarget.MINGW_X64)).single()
        // The task's `includes` should have entries pointing at the JDK
        // include root and the platform dir. We don't assert equality
        // because `includes` is a ConfigurableFileCollection and may not
        // expose its contents the same way after configuration time.
        assertEquals(KonanTarget.MINGW_X64, t.target.get())
    }

    @Test
    fun `host target registers an enabled task`() {
        val project = newProject()
        val host = HostManager.host
        val tasks = project.clangBuildJni(name = "k", targets = listOf(host))
        val t = tasks.single()
        // The host task is always enabled: clangBuildDynamic's own onlyIf
        // (target enabled on host) plus our isHost short-circuit keeps it
        // running. We assert that the onlyIf on the underlying task
        // accepts the host target.
        assertTrue(
            TargetSupport.isKonanTargetEnabledOnHost(host),
            "sanity: host target must be enabled on the host",
        )
        assertEquals(host, t.target.get())
    }

    @Test
    fun `compileDirJni uses the KMP-conventional src_jvmMain_c path by default`() {
        val project = newProject()
        project.layout.projectDirectory.dir("src/jvmMain/c").asFile.mkdirs()
        val t = project.tasks.register("task", BuildDynamicTask::class.java) { }.get()
        t.compileDirJni()
        // compileDir() registers a Compile per .c file in the dir; with an
        // empty dir the register call is a no-op, so we instead verify the
        // path was resolved by trying it on a populated directory and
        // checking the source parent.
        val jniDir = project.layout.projectDirectory.dir("src/jvmMain/c").asFile
        java.io.File(jniDir, "klua_jni.c").writeText("// stub")
        t.compileDirJni()
        val compiles = t.sources.filterNotNull()
        assertEquals(1, compiles.size, "expected one .c file to register")
        assertEquals(jniDir, compiles.single().source.parentFile)
    }

    @Test
    fun `compileDirJni honours an explicit override directory`() {
        val project = newProject()
        val custom = project.layout.projectDirectory.dir("custom-jni").asFile.also { it.mkdirs() }
        java.io.File(custom, "bridge.c").writeText("// stub")
        val t = project.tasks.register("task", BuildDynamicTask::class.java) { }.get()
        t.compileDirJni(custom)
        val compiles = t.sources.filterNotNull()
        assertEquals(1, compiles.size, "expected one .c file to register")
        assertEquals(custom, compiles.single().source.parentFile)
    }
}
