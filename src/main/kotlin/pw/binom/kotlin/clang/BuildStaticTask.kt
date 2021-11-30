package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

open class BuildStaticTask : DefaultTask() {
    private class Compile(val source: File, val objectFile: File, val args: List<String>?)

    @Input
    var target: KonanTarget = HostManager.host

    private var compiles = ArrayList<Compile>()
    private val includes = ArrayList<File>()

    /**
     * Allow to use multithreding
     */
    @Input
    var multiThread = true

    @Input
    val compileArgs = ArrayList<String>()

    @OutputFile
    var staticFile: File? = null

    @Input
    var debugBuild: Boolean = false

    @Input
    var optimizationLevel: Int = 2

    @InputFiles
    val inputSourceFiles = ArrayList<File>()

    @OutputFiles
    val outputObjectFiles = ArrayList<File>()

    private val nativeObjDir by lazy {
        project.buildDir.resolve("native").resolve("obj").resolve(target.name)
    }

    fun compileArgs(vararg args: String) {
        this.compileArgs.addAll(args)
    }

    fun include(vararg includes: File) {
        this.includes.addAll(includes)
    }

    @JvmOverloads
    fun compileDir(sourceDir: File, objectDir: File, args: List<String>? = null, filter: ((File) -> Boolean)? = null) {
        logger
        sourceDir.list()?.forEach {
            val f = sourceDir.resolve(it)
            if (f.isFile && (f.extension.toLowerCase() == "c" || f.extension.toLowerCase() == "cpp")) {
                if (filter == null || filter(f)) {
                    compileFile(
                        source = f,
                        args = args,
                        objectDir = objectDir,
                    )
                }
            }

            if (f.isDirectory && (filter == null || filter(f))) {
                compileDir(
                    sourceDir = f,
                    objectDir = objectDir.resolve(it),
                    args = args,
                    filter = filter
                )
            }
        }
    }

    @JvmOverloads
    fun compileFile(source: File, objectDir: File, args: List<String>? = null) {
        val outFile =
            objectDir.resolve("${source.nameWithoutExtension}.o")
        compiles.add(
            Compile(
                source = source,
                objectFile = outFile,
                args = args
            )
        )
        inputSourceFiles.add(source)
        outputObjectFiles.add(outFile)
    }

    private class CompileResult(val source: File, val code: Int, val result: String)

    @TaskAction
    fun execute() {
        if (staticFile == null) {
            throw InvalidUserDataException("Static output file not set")
        }
        if (optimizationLevel < 0 || optimizationLevel > 3)
            throw InvalidUserDataException("Invalid Optimization Level")
        if (!HostManager().isEnabled(target)) {
            throw StopActionException("Target ${target.name} not supported")
        }
        val env = HashMap<String, String>()
        if (HostManager.hostIsMac && target == KonanTarget.MACOS_X64) {
            env["CPATH"] =
                "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include"
        }
        val osPathSeparator = if (HostManager.hostIsMingw) {
            ';'
        } else {
            ':'
        }
        env["PATH"] = "$HOST_LLVM_BIN_FOLDER$osPathSeparator${System.getenv("PATH")}"

        Konan.checkSysrootInstalled(target)
        val targetInfo = targetInfoMap.getValue(target)
        fun runCompile(compile: Compile): CompileResult =
            run {
                val args = ArrayList<String>()
                args.add(targetInfo.llvmDir.resolve("clang").executable.absolutePath)
                args.add("-O$optimizationLevel")
                args.add("-fexceptions")
                args.add("-c")
                args.add("-fno-stack-protector")
                args.add("-Wall")
                if (targetInfo.toolchain != null) {
                    args.add("--gcc-toolchain=${targetInfo.toolchain}")
                }
                args.add("--target=${targetInfo.targetName}")
                targetInfo.sysRoot.forEach {
                    args.add("--sysroot=$it")
                }
                args.addAll(targetInfo.clangCompileArgs)
                args.addAll(this.compileArgs)
                if (compile.args != null) {
                    args.addAll(compile.args)
                }
                args.add("-o")
                args.add(compile.objectFile.absolutePath)
                args.add(compile.source.absolutePath)
                includes.forEach {
                    args.add("-I${it.absolutePath}")
                }
                val builder = ProcessBuilder(
                    args
                )
                builder.environment().putAll(env)
                val process = builder.start()

                val stdout = StreamGobbler(process.inputStream)
                val stdin = StreamGobbler(process.errorStream)
                stdout.start()
                stdin.start()
                process.waitFor()
                stdout.join()
                stdin.join()
                if (process.exitValue() == 0) {
                    logger.lifecycle("Compile ${compile.source}: OK")
                }
                CompileResult(
                    code = process.exitValue(),
                    result = stdout.out.toString() + stdin.out.toString(),
                    source = compile.source
                )
            }


        var threadPool: ExecutorService? = null
        if (multiThread) {
            threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        }
        val errorExist = AtomicBoolean(false)
        val tasks = compiles.mapNotNull {
            if (!it.source.isFile) {
                logger.warn("Compile ${it.source}: Source not found")
                return@mapNotNull null
            }
            if (it.objectFile.isFile && it.objectFile.lastModified() > it.source.lastModified()) {
                logger.info("Compile ${it.source}: UP-TO-DATE")
                return@mapNotNull null
            }
            Callable {
                if (!errorExist.get()) {
                    val c = runCompile(it)
                    if (c.code != 0)
                        errorExist.set(true)
                    c
                } else
                    CompileResult(
                        source = File(""),
                        code = 1,
                        result = ""
                    )
            }
        }

        val results = if (threadPool != null) {
            threadPool.invokeAll(tasks).map { it.get() }
        } else {
            tasks.map {
                it.call()
            }
        }

        results.forEach {
            if (it.code != 0) {
                println("Compile ${it.source}: FAIL")
                throw GradleScriptException(
                    "Can't build \"${it.source}\".", RuntimeException(
                        "Output:\n${it.result}"
                    )
                )
            }
        }
        val ar = targetInfo.llvmDir.resolve("llvm-ar").executable
        if (!ar.isFile) {
            throw RuntimeException("File $ar not found")
        }
        val args = ArrayList<String>()
        args.add(ar.absolutePath)
        args.add("rc")
        args.add(staticFile!!.absolutePath)
        compiles.forEach {
            args.add(it.objectFile.absolutePath)
        }

        val builder = ProcessBuilder(
            args
        )
        builder.directory(staticFile!!.parentFile)
        builder.environment().put("PATH", "$HOST_LLVM_BIN_FOLDER;${System.getenv("PATH")}")
        val process = builder.start()
        StreamGobblerAppendable(process.inputStream, System.out, false).start()
        StreamGobblerAppendable(process.errorStream, System.err, false).start()
        process.waitFor()
        if (process.exitValue() != 0) {
            throw GradleScriptException(
                "Can't execute link static library",
                RuntimeException("Can't link: Linked returns ${process.exitValue()}")
            )
        }
    }

    private val File.executable
        get() = when (HostManager.host.family) {
            Family.MINGW -> File("$this.exe")
            else -> this
        }
}