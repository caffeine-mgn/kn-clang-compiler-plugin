package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

abstract class BuildStaticTask : DefaultTask() {
    class Compile(val source: File, val objectFile: File, val args: List<String>?)

    @get:Input
    abstract val target: Property<KonanTarget>
    fun target(target: KonanTarget) {
        this.target.set(target)
    }


    private var compiles = ArrayList<Compile>()

    @get:Internal
    val sources: List<Compile>
        get() = compiles

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val includes: ConfigurableFileCollection

    /**
     * Allow to use multithreding
     */
    @get:Input
    var multiThread = true

    @get:Input
    val compileArgs = ArrayList<String>()

    @get:OutputFile
    abstract val staticFile: RegularFileProperty


    @get:Input
    abstract val debugEnabled: Property<Boolean>

    fun debugEnabled(debugEnabled: Boolean) {
        this.debugEnabled.set(debugEnabled)
    }

    @get:Input
    abstract val optimizationLevel: Property<Int>


    fun optimizationLevel(level: Int) {
        this.optimizationLevel.set(level)
    }


    private val selectedTarget
        get() = target.getOrElse(HostManager.host)

    @get:OutputDirectory
    abstract val objectDirectory: RegularFileProperty

    fun objectDirectory(file: File) {
        this.objectDirectory.set(file)
    }

    fun objectDirectory(file: Any?) {
        val file = project.fileAnyWay(file) ?: throw IllegalArgumentException("Can't cast $file to File")
        objectDirectory(file)
    }

    private val nativeObjDir by lazy {
        if (objectDirectory.isPresent)
            objectDirectory.asFile.get()
        else {
            project.buildDir.resolve("native").resolve(name).resolve("obj").resolve(selectedTarget.name)
        }
    }

    fun compileArgs(vararg args: String) {
        this.compileArgs.addAll(args)
    }

    fun include(vararg includes: File) {
        this.includes.from(*includes)
    }

    fun include(vararg includes: Any) {
        include(
            *includes.mapNotNull {
                project.fileAnyWay(it)
            }.toTypedArray()
        )
    }

    @JvmOverloads
    fun compileDir(
        sourceDir: File,
        objectDir: File = nativeObjDir,
        args: List<String>? = null,
        filter: ((File) -> Boolean)? = null
    ) {
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
    fun compileFile(source: File, objectDir: File? = null, args: List<String>? = null) {
        val objectDir = objectDir ?: nativeObjDir
        val outFile =
            objectDir.resolve("${source.nameWithoutExtension}.o")
        compiles.add(
            Compile(
                source = source,
                objectFile = outFile,
                args = args
            )
        )
        inputs.file(source)
        outputs.file(outFile)
    }

    private class CompileResult(val source: File, val code: Int, val result: String)

    @TaskAction
    fun execute() {
        if (!TargetSupport.isKonancTargetSupportOnHost(target.get())) {
            logger.warn("Compile target ${target.get()} not supported on host ${HostManager.host.name}")
            return
        }
        if (!staticFile.isPresent) {
            throw InvalidUserDataException("Static output file not set")
        }
        if (optimizationLevel.get() < 0 || optimizationLevel.get() > 3) {
            throw InvalidUserDataException("Invalid Optimization Level")
        }
        if (!HostManager().isEnabled(selectedTarget)) {
            throw StopActionException("Target ${selectedTarget.name} not supported")
        }
        if (!TargetSupport.isTargetSupport(selectedTarget)) {
            throw IllegalArgumentException("Target ${selectedTarget.name} is not supported")
        }
        val env = HashMap<String, String>()
        if (HostManager.hostIsMac && selectedTarget == KonanTarget.MACOS_X64) {
            env["CPATH"] =
                "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/usr/include"
        }
        val osPathSeparator = if (HostManager.hostIsMingw) {
            ';'
        } else {
            ':'
        }
        env["PATH"] = "$HOST_LLVM_BIN_FOLDER$osPathSeparator${System.getenv("PATH")}"

        Konan.checkSysrootInstalled(selectedTarget)
        val targetInfo = targetInfoMap.getValue(selectedTarget)
        fun runCompile(compile: Compile): CompileResult =
            run {
                val args = ArrayList<String>()
                args.add(targetInfo.llvmDir.resolve("clang").executable.absolutePath)
                args.add("-O${optimizationLevel.get()}")
                args.add("-fexceptions")
                if (debugEnabled.get()) {
                    args.add("-g")
                }
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
        args.add(staticFile.asFile.get().absolutePath)
        compiles.forEach {
            args.add(it.objectFile.absolutePath)
        }

        val builder = ProcessBuilder(
            args
        )
        builder.directory(staticFile.asFile.get().parentFile)
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
}

internal val File.executable
    get() = when (HostManager.host.family) {
        Family.MINGW -> parentFile.resolve("$name.exe")
        else -> this
    }