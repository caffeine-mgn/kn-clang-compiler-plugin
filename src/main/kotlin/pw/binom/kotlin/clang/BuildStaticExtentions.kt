package pw.binom.kotlin.clang

import org.gradle.api.Project
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName


fun Project.clangBuildStatic(
    target: KonanTarget = HostManager.host,
    name: String = "native",
    taskName: String = "buildStatic${name.capitalize()}${target.presetName.capitalize()}",
    config: BuildStaticTask.() -> Unit
):BuildStaticTask {
    val task = tasks.register(taskName, BuildStaticTask::class.java).get()
    task.target.set(target)
    task.target.finalizeValue()
    task.group = "build"
    task.objectDirectory.set(buildDir.resolve("native").resolve(name).resolve(target.name).resolve("obj"))
    task.staticFile.set(
        buildDir.resolve("native").resolve(name).resolve(target.name).resolve("static").resolve("lib$name.a")
    )
    task.onlyIf { TargetSupport.isTargetSupportOnHost(target) }
    task.config()
    return task
}