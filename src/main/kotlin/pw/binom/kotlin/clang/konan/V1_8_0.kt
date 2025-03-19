package pw.binom.kotlin.clang.konan

import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import pw.binom.kotlin.clang.*
import java.io.File


// -Wno-builtin-macro-redefined

object V1_8_0 : BaseKonanVersion() {
    override val HOST_KONAN_LLVM_DIR_NAME: String
        get() = HOST_KONAN_LLVM11_DIR_NAME
    override val LLVM_VERSION: String
        get() = "11.1.0"
    private val targets = mapOf(
        KonanTarget.MINGW_X64 to TargetInfo(
            targetName = "x86_64-pc-windows-gnu",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("msys2-mingw-w64-x86_64-2")),
            llvmDir = LLVM_BIN,
        ),
//        KonanTarget.MINGW_X86 to TargetInfo(
//            targetName = "i686-w64-mingw32",
//            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("msys2-mingw-w64-i686-2")),
//            llvmDir = LLVM_BIN,
//        ),
        KonanTarget.LINUX_X64 to TargetInfo(
            targetName = "x86_64-unknown-linux-gnu",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("$LINUX_X64_SYSROOT/x86_64-unknown-linux-gnu/sysroot")),
            llvmDir = LLVM_BIN,
            clangCompileArgs = listOf("-fPIC"),
            toolchain = DEPENDENCIES_FOLDER.resolve(LINUX_X64_SYSROOT),
        ),
        KonanTarget.LINUX_ARM32_HFP to TargetInfo(
            targetName = "armv6-unknown-linux-gnueabihf",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2/arm-unknown-linux-gnueabihf/sysroot")),
            clangCompileArgs = listOf("-mfpu=vfp", "-mfloat-abi=hard"),
            llvmDir = LLVM_BIN,
            toolchain = DEPENDENCIES_FOLDER.resolve("arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2"),
        ),
        KonanTarget.LINUX_ARM64 to TargetInfo(
            targetName = "aarch64-unknown-linux-gnu",
            sysRoot = listOf(DEPENDENCIES_FOLDER.resolve("$LINUX_ARM64/aarch64-unknown-linux-gnu/sysroot")),
            clangCompileArgs = listOf(/*"-mfpu=vfp", "-mfloat-abi=hard", */"-fPIC"),
            llvmDir = LLVM_BIN,
            toolchain = DEPENDENCIES_FOLDER.resolve(LINUX_ARM64),
        ),
    )

    override fun findTargetInfo(target: KonanTarget): TargetInfo? = targets[target]
}
