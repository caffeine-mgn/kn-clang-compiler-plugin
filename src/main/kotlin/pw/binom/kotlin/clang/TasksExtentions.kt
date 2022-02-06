package pw.binom.kotlin.clang

import org.gradle.api.tasks.TaskContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

val KotlinNativeLink.targetKonan
    get() = binary.target.konanTarget

val KotlinNativeTarget.compileTaskName
    get() = "compileKotlin${name.capitalize()}"

fun KotlinMultiplatformExtension.eachNative(func: KotlinNativeTarget.() -> Unit) {
    this.targets.forEach {
        if (it is KotlinNativeTarget) {
            it.func()
        }
    }
}

fun TaskContainer.eachKotlinNativeLink(
    release: Boolean = true,
    debug: Boolean = false,
    test: Boolean = false,
    func: (KotlinNativeLink) -> Unit
) {
    toTypedArray()
        .mapNotNull { it as? KotlinNativeLink }
        .filter {
            if (!release && it.binary.buildType == NativeBuildType.RELEASE) {
                return@filter false
            }
            if (!debug && it.binary.buildType == NativeBuildType.RELEASE) {
                return@filter false
            }
            if (!test && it.processTests) {
                return@filter false
            }
            true
        }.forEach(func)
}
