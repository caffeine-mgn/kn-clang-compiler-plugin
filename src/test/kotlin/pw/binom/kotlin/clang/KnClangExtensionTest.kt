package pw.binom.kotlin.clang

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class KnClangExtensionTest {
    @Test
    fun defaultKonanVersionIs24_20() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("pw.binom.kn-clang")
        val ext = project.extensions.getByType(KnClangExtension::class.java)
        assertEquals("2.4.20", ext.konanVersion.get())
    }

    @Test
    fun extensionVersionPropagatesToDownloadKonanTask() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("pw.binom.kn-clang")
        val ext = project.extensions.getByType(KnClangExtension::class.java)
        ext.konanVersion.set("2.3.20")
        val task = project.tasks.named("downloadKonan").get() as KonanDownloadTask
        assertEquals("2.3.20", task.konanVersion.get())
    }

    @Test
    fun taskLevelOverrideWinsOverExtension() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("pw.binom.kn-clang")
        val ext = project.extensions.getByType(KnClangExtension::class.java)
        ext.konanVersion.set("2.3.20")
        val task = project.tasks.named("downloadKonan").get() as KonanDownloadTask
        task.konanVersion.set("2.1.0")
        assertEquals("2.1.0", task.konanVersion.get())
    }
}