package pw.binom.kotlin.clang


import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

data class TargetInfo(
    val targetName: String,
    val sysRoot: List<File>,
    val clangCompileArgs: List<String> = emptyList(),
    val llvmDir: File,
    val toolchain: File? = null,
)

val KONAN_USER_DIR = File(System.getenv("KONAN_DATA_DIR") ?: "${System.getProperty("user.home")}/.konan")
val KONAN_DEPS = KONAN_USER_DIR.resolve("dependencies")

fun PREBUILD_KONAN_DIR_NAME(version: KonanVersionNumber) = when {
    HostManager.hostIsLinux -> "kotlin-native-prebuilt-linux-x86_64-$version"
    HostManager.hostIsMac && System.getProperty("os.arch") == "aarch64" -> "kotlin-native-prebuilt-macos-aarch64-$version"
    HostManager.hostIsMac -> "kotlin-native-prebuilt-macos-x86_64-$version"
    HostManager.hostIsMingw -> "kotlin-native-prebuilt-windows-x86_64-$version"
    else -> error("Unknown host OS")
}
