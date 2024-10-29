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

    private var compiles = ArrayList<Compile?>()

    @get:Internal
    val sources: List<Compile?>
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

    @get:Input
    @get:Optional
    abstract val konanVersion: Property<String>

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
        objectDirectory(project.fileAnyWay(file) ?: throw IllegalArgumentException("Can't cast $file to File"))
    }

    private val nativeObjDir by lazy {
        if (objectDirectory.isPresent) {
            objectDirectory.asFile.get()
        } else {
            project.layout.buildDirectory.file("native/$name/obj/${selectedTarget.name}").get().asFile
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
            }.toTypedArray(),
        )
    }

    @JvmOverloads
    fun compileDir(
        sourceDir: File,
        objectDir: File = nativeObjDir,
        args: List<String>? = null,
        filter: ((File) -> Boolean)? = null,
    ) {
        sourceDir.list()?.forEach {
            val f = sourceDir.resolve(it)
            if (f.isFile && (f.extension.lowercase() == "c" || f.extension.lowercase() == "cpp")) {
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
                    filter = filter,
                )
            }
        }
    }

    @JvmOverloads
    fun compileFile(source: File, objectDir: File? = null, args: List<String>? = null) {
        val newObjectDir = objectDir ?: nativeObjDir
        val outFile = newObjectDir.resolve("${source.nameWithoutExtension}.o")
        compiles.add(
            Compile(
                source = source,
                objectFile = outFile,
                args = args,
            ),
        )
        inputs.file(source)
        outputs.file(outFile)
    }

    private class CompileResult(val source: File, val c: CppCompiler.CompileResult)

    private fun getKonanCompileVersion() =
        if (konanVersion.isPresent) {
            Version(konanVersion.get())
        } else {
            Version(KotlinVersion.CURRENT.toString())
        }

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

        Konan.checkSysrootInstalled(version = getKonanCompileVersion(), target = selectedTarget)
        val konan = KonanVersion.getVersion(getKonanCompileVersion())
        val compiller = konan.getCppCompiler(selectedTarget)
        val linker = konan.getLinked(selectedTarget)
        fun runCompile(compile: Compile): CompileResult =
            run {
                val result = compiller.compile(
                    inputFiles = compile.source,
                    outputFile = compile.objectFile,
                    logger = logger,
                    args = (compile.args ?: emptyList()) + compileArgs,
                )
                return CompileResult(
                    source = compile.source,
                    c = result,
                )
                /*
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
                    args,
                )
                logger.info("Executing ${args.joinToString(" ")}")
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
                    source = compile.source,
                )
                */
            }

        var threadPool: ExecutorService? = null
        if (multiThread) {
            threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        }
        val errorExist = AtomicBoolean(false)
        val tasks = compiles.filterNotNull().mapNotNull {
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
                    if (c.c is CppCompiler.CompileResult.Error) {
                        errorExist.set(true)
                    }
                    c
                } else {
                    null
                }
            }
        }

        val results = if (threadPool != null) {
            threadPool.invokeAll(tasks).map { it.get() }
        } else {
            tasks.map {
                it.call()
            }
        }

        results.filterNotNull().forEach {
            val result = it.c
            if (result is CppCompiler.CompileResult.Error) {
                println("Compile ${it.source}: FAIL")

                throw GradleScriptException(
                    "Can't build \"${it.source}\".",
                    RuntimeException(
                        "Output:\n${result.stderr}",
                    ),
                )
            }
        }

        linker.static(
            objectFiles = compiles.filterNotNull().map {
                it.objectFile
            },
            output = staticFile.asFile.get(),
        )
        /*
        val ar = targetInfo.llvmDir.resolve("llvm-ar").executable
        if (!ar.isFile) {
            throw RuntimeException("File $ar not found")
        }
        val args = ArrayList<String>()
        args.add(ar.absolutePath)
        args.add("rc")
        args.add(staticFile.asFile.get().absolutePath)
        compiles.filterNotNull().forEach {
            args.add(it.objectFile.absolutePath)
        }

        val builder = ProcessBuilder(
            args,
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
                RuntimeException("Can't link: Linked returns ${process.exitValue()}"),
            )
        }
        */
    }
}

internal val File.executable
    get() = when (HostManager.host.family) {
        Family.MINGW -> parentFile.resolve("$name.exe")
        else -> this
    }
