package pw.binom.kotlin.clang

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.konan.target.KonanTarget

class ClangPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val knClang = target.extensions.create("knClang", KnClangExtension::class.java)

        val downloadKonan = target.tasks.register("downloadKonan", KonanDownloadTask::class.java).get()
        downloadKonan.description = "Downloads konan to user home directory"
        downloadKonan.group = "konan"
        downloadKonan.konanVersion.convention(knClang.konanVersion)

        ALL_CROSS_TARGETS.forEach { kt ->
            val task = target.tasks.register(
                "downloadKonanToolchain${kt.name.replaceFirstChar { c -> c.uppercase() }}",
                KonanDownloadToolchainTask::class.java,
            ).get()
            task.description = "Downloads toolchain + sysroot for target ${kt.name}"
            task.group = "konan"
            task.konanVersion.convention(knClang.konanVersion)
            task.target.set(kt)
        }
    }

    private companion object {
        val ALL_CROSS_TARGETS: List<KonanTarget> = listOf(
            KonanTarget.LINUX_X64,
            KonanTarget.LINUX_ARM64,
            KonanTarget.LINUX_ARM32_HFP,
            KonanTarget.MINGW_X64,
            KonanTarget.ANDROID_ARM32,
            KonanTarget.ANDROID_ARM64,
            KonanTarget.ANDROID_X86,
            KonanTarget.ANDROID_X64,
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
        )
    }
}