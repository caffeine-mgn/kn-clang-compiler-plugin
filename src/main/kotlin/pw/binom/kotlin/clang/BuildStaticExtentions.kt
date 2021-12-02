package pw.binom.kotlin.clang

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinNativeCompilation
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.presetName

fun AbstractKotlinNativeCompilation.addStatic(vararg files: Any?) {
    val args = ArrayList<String>(files.size * 2 + kotlinOptions.freeCompilerArgs.size)
    kotlinOptions.freeCompilerArgs.forEach {
        args += it
    }
    files.forEach {
        val file = project.fileAnyWay(it)?:return@forEach
        args += "-include-binary"
        args += file.absolutePath
    }
    kotlinOptions.freeCompilerArgs = args
}


fun Project.clangBuildStatic(
    target: KonanTarget = HostManager.host,
    name: String = "native",
    taskName: String = "buildStatic${name.capitalize()}${target.presetName.capitalize()}",
    config: BuildStaticTask.() -> Unit
): BuildStaticTask {
    val task = tasks.register(taskName, BuildStaticTask::class.java).get()
    task.target.set(target)
    task.target.finalizeValue()
    task.debugEnabled.set(false)
    task.group = "build"
    task.optimizationLevel(2)
    task.objectDirectory.set(buildDir.resolve("native").resolve(name).resolve(target.name).resolve("obj"))
    task.staticFile.set(
        buildDir.resolve("native").resolve(name).resolve(target.name).resolve("static").resolve("lib$name.a")
    )
    task.onlyIf { TargetSupport.isKonancTargetSupportOnHost(target) }
    task.config()
    return task
}