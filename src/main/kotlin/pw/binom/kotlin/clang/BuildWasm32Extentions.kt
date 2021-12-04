package pw.binom.kotlin.clang

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

fun Project.clangBuildWasm32(
    name: String = "native",
    taskName: String = "buildBinary${name.capitalize()}Wasm32",
    config: BuildWasm32.() -> Unit
): TaskProvider<BuildWasm32> {
    val task = tasks.register(taskName, BuildWasm32::class.java)
    task.configure { task ->
        task.group = "build"
        task.ltoOptimizationLevel.set(2)
        task.exportAll.set(false)
//        task.objects.from(KONAN_DEPS.resolve(WASM32_SYSROOT_NAME).resolve("lib/libc.symbols"))
        task.output.set(
            buildDir.resolve("native")
                .resolve(name)
                .resolve("wasm32")
                .resolve("binary")
                .resolve("$name.wasm")
        )
        config(task)
    }
    return task
}