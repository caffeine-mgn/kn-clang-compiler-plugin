package pw.binom.kotlin.clang

import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.util.internal.VersionNumber
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

abstract class BuildTask : DefaultTask() {
    data class Compile(val source: File, val objectFile: File, val args: List<String>?)

    @get:Input
    abstract val target: Property<KonanTarget>
    fun target(target: KonanTarget) {
        this.target.set(target)
    }

    @get:Internal
    protected var compiles = ArrayList<Compile?>()

    @get:Internal
    val sources: List<Compile?>
        get() = compiles

    @get:Input
    val compileArgs = ArrayList<String>()

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

    /**
     * Allow to use multithreding
     */
    @get:Input
    var multiThread = true

    fun optimizationLevel(level: Int) {
        this.optimizationLevel.set(level)
    }

    @get:Internal
    protected val selectedTarget
        get() = target.getOrElse(HostManager.host)

    @get:OutputDirectory
    abstract val objectDirectory: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val includes: ConfigurableFileCollection

    fun objectDirectory(file: File) {
        this.objectDirectory.set(file)
    }

    fun objectDirectory(file: Any?) {
        objectDirectory(project.fileAnyWay(file) ?: throw IllegalArgumentException("Can't cast $file to File"))
    }

    @get:Internal
    protected val nativeObjDir by lazy {
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

    protected data class CompileResult(
        val source: File,
        val c: CppCompiler.CompileResult,
        val args: List<String>,
    )

    @Internal
    protected fun getKonanCompileVersion() =
        if (konanVersion.isPresent) {
            VersionNumber.parse(konanVersion.get())
        } else {
            VersionNumber.parse(KotlinVersion.CURRENT.toString())
        }

    protected fun compileAll() {
        if (optimizationLevel.get() < 0 || optimizationLevel.get() > 3) {
            throw InvalidUserDataException("Invalid Optimization Level")
        }
        if (!HostManager().isEnabled(selectedTarget)) {
            throw StopActionException("Target ${selectedTarget.name} not supported")
        }
        if (!TargetSupport.isTargetSupport(selectedTarget)) {
            throw IllegalArgumentException("Target ${selectedTarget.name} is not supported")
        }

        Konan.checkSysrootInstalled(version = getKonanCompileVersion(), target = selectedTarget)

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

        val konan = KonanVersion.getVersion(getKonanCompileVersion())
        val compiller = konan.getCppCompiler(selectedTarget)

        fun runCompile(compile: Compile): CompileResult =
            run {
                val args =
                    (compile.args ?: emptyList()) + compileArgs + includes.flatMap { listOf("-I", it.absolutePath) }
                val result = compiller.compile(
                    inputFiles = compile.source,
                    outputFile = compile.objectFile,
                    logger = logger,
                    args = args,
                )
                return CompileResult(
                    source = compile.source,
                    c = result,
                    args = args,
                )
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
                        "Command:\n${result.cmd.joinToString(" ")}\n\n" +
                                "Output:\n${result.stderr}",
                    ),
                )
            }
        }
    }
}