# Building with external tools (CMake, Make, …)

The plugin ships Kotlin/Native's Clang toolchain — LLVM binaries, target sysroot, and
per-target flags — fully resolved from the selected Kotlin/Native distribution. You don't
have to drive it through `clangBuildStatic` / `clangBuildDynamic`: the same metadata is
exposed programmatically and can be handed off to any external build system that consumes
a C/C++ toolchain (CMake, GNU Make, Meson, Bazel rules_cc, …).

This page shows how to extract the toolchain for a given `KonanTarget` and feed it into
CMake as a toolchain file.

## Why you might want this

`clangBuildStatic` and `clangBuildDynamic` compile sources you point them at (via
`compileDir` / `compileFile`) and link them into a single static or shared library. That
covers the simple cases. For anything more involved — multi-file projects, generated code,
mixed C/C++/Objective-C, autoconf/CMake-based third-party libraries — it's usually easier
to let the project's own build system orchestrate the compilation, and just give it the
right compiler, sysroot, and flags.

The plugin already knows all of those: it parses `konan.properties`, locates the LLVM
binaries and the per-target sysroot, and computes the `-target` / `--sysroot` /
`--gcc-toolchain` / `-B<ndk>/bin` flag set used internally. That data is available through
the `KonanVersion` / `TargetInfo` API.

## What you'll get

For a target like `KonanTarget.ANDROID_X86`, the resolved toolchain is:

| Field        | Example (`ANDROID_X86`, K/N 2.4.20)                                                |
|--------------|------------------------------------------------------------------------------------|
| `compiler`   | `<ndk>/bin/i686-linux-android21-clang` (NDK wrapper)                               |
| `cxxCompiler`| same path with `++` suffix                                                         |
| `archiver`   | `<llvm>/bin/llvm-ar` (static archives)                                             |
| `linker`     | `<llvm>/bin/lld` (`ld.lld`)                                                        |
| `sysRoot`    | `~/.konan/dependencies/.../android-21/arch-x86`                                    |
| `toolchain`  | `null` for Android; `<llvm>/../gcc-toolchain` for Linux                            |
| `triple`     | `i686-unknown-linux-android`                                                       |
| `cFlags`     | `-O2 -target i686-unknown-linux-android -fexceptions -isystem … -B<ndk>/bin …`      |

`KnClangToolchain` doesn't carry CMake choices like the C++ stdlib flag (`-lc++_static`,
`-lc++`, `-lstdc++`) or `-fuse-ld=lld` — those depend on `target.family` and are derived
at the call site (see [Generating a CMake toolchain file](#generating-a-cmake-toolchain-file)).

For Linux targets `compiler` is `<llvm>/bin/clang`; for Apple targets on a macOS host the
sysroot comes from the installed Xcode (the path returned by `Xcode.current`), not from
`konan.properties`.

## Extracting the toolchain from a build script

`KnClangExtension` exposes `knClang.toolchain(target)` which returns a fully-resolved
`KnClangToolchain` (compiler, C++ compiler, archiver, linker, sysroot, triple, ready-to-use
`cFlags`). It downloads the target's sysroot on first call via
`Konan.checkSysrootInstalled`, and is safe to call at task execution time.

```kotlin
// build.gradle.kts
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("pw.binom.kn-clang") version "0.0.7"
}

knClang {
    konanVersion.set("2.4.20")
}

// Resolves the toolchain lazily — sysroot is downloaded on the first .get() / toolchain()
// call, not at configuration time.
val androidX86 = knClang.toolchain(KonanTarget.ANDROID_X86)
val linuxArm   = knClang.toolchain(KonanTarget.LINUX_ARM64)

// Inspect it:
androidX86.compiler      // <ndk>/bin/i686-linux-android21-clang
androidX86.cxxCompiler   // <ndk>/bin/i686-linux-android21-clang++
androidX86.archiver      // <llvm>/bin/llvm-ar
androidX86.linker        // <llvm>/bin/lld
androidX86.sysRoot       // ~/.konan/dependencies/.../android-21/arch-x86
androidX86.triple        // i686-unknown-linux-android
androidX86.cFlags        // -O2 -target … -fexceptions -isystem … -B<ndk>/bin … --sysroot=…
```

The data class lives in `pw.binom.kotlin.clang` and is the same one the implementation
uses internally, just exposed in a stable shape so consumers can depend on it.

The Apple-specific `-fuse-ld=lld` switch is **not** part of `cFlags` (it's invalid on
Apple — see [Caveats](#caveats)); pass it manually for non-Apple targets. The C++ stdlib
flag is also a per-family choice and isn't in the data class — derive it from
`target.family` when generating the CMake toolchain file, as shown below.

If you only ever need a single target, the same code can live inside a `tasks.register`
configuration block and feed the result into a CMake invocation task — see the CMake
section below.

## Generating a CMake toolchain file

CMake expects toolchain definitions in a `.cmake` file passed via `-DCMAKE_TOOLCHAIN_FILE=…`.
The plugin doesn't generate one yet, but it's a few lines of Kotlin:

```kotlin
// build.gradle.kts
val outDir = layout.buildDirectory.dir("cmake").get().asFile
val toolchainFile = outDir.resolve("kn-android-x86.cmake")

// C++ stdlib + linker-flag choices are target-family-specific. The plugin doesn't bake
// them into the toolchain object — derive them at the call site.
val isApple = androidX86.target.family.isAppleFamily
val cxxStdLib = when (androidX86.target.family) {
    Family.ANDROID       -> "-lc++_static -lc++abi"
    Family.OSX, Family.IOS, Family.TVOS, Family.WATCHOS -> "-lc++"
    else                 -> "-lstdc++"
}

val generateToolchain = tasks.register("generateAndroidX86Toolchain") {
    val t = androidX86
    val flagsInit = t.cFlags.joinToString(" ")
    val linkerInit = buildList {
        if (!isApple) add("-fuse-ld=lld")  // invalid on Apple — see Caveats
        add(cxxStdLib)
    }.joinToString(" ")

    inputs.property("flags", flagsInit)
    inputs.property("linker", linkerInit)
    outputs.file(toolchainFile)

    doLast {
        toolchainFile.parentFile.mkdirs()
        toolchainFile.writeText(
            """
            set(CMAKE_SYSTEM_NAME Android)
            set(CMAKE_SYSTEM_VERSION 21)
            set(CMAKE_C_COMPILER   "${t.compiler}")
            set(CMAKE_CXX_COMPILER "${t.cxxCompiler}")
            set(CMAKE_AR           "${t.archiver}")
            set(CMAKE_RANLIB       "${t.archiver}")
            set(CMAKE_SYSROOT      "${t.sysRoot}")
            set(CMAKE_C_COMPILER_TARGET ${t.triple})
            set(CMAKE_CXX_COMPILER_TARGET ${t.triple})

            # clangCompileArgs already contains -target, --sysroot,
            # -B<ndk>/bin, -isystem <llvm>/lib/clang/.../include,
            # -D__ANDROID_API__=21, etc. Append-only extras here.
            set(CMAKE_C_FLAGS_INIT   "$flagsInit")
            set(CMAKE_CXX_FLAGS_INIT "$flagsInit -stdlib=libc++")

            set(CMAKE_SHARED_LINKER_FLAGS_INIT  "$linkerInit")
            set(CMAKE_EXE_LINKER_FLAGS_INIT     "$linkerInit")
            """.trimIndent()
        )
    }
}
```

For a Linux target, swap `CMAKE_SYSTEM_NAME` for `Linux`, drop `CMAKE_SYSTEM_VERSION`, and
use the resolved `t.toolchain` (`<linux-toolchain>`) for `--gcc-toolchain` — it's already
embedded in `cFlags`, so nothing extra is needed there. For Apple targets the equivalent
toolchain is more involved (CMake doesn't have first-class iOS/tvOS/watchOS toolchains —
the popular approach is the
[ios-cmake](https://github.com/leetal/ios-cmake) project, which provides per-platform
toolchain files you point CMake at while passing the toolchain above as the compiler
front-end).

## Driving CMake from Gradle

Once the toolchain file is on disk, hand it to CMake the usual way:

```kotlin
val configure = tasks.register("configureAndroidX86") {
    dependsOn(generateToolchain)
    val buildDir = layout.buildDirectory.dir("cmake-build/android-x86").get().asFile
    inputs.file(toolchainFile)
    outputs.dir(buildDir)
    doLast {
        project.exec {
            commandLine(
                "cmake",
                "-S", file("src/main/cpp").absolutePath,
                "-B", buildDir.absolutePath,
                "-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.absolutePath}",
                "-DCMAKE_BUILD_TYPE=Release",
            )
        }
    }
}

val build = tasks.register("buildAndroidX86") {
    dependsOn(configure)
    doLast {
        project.exec {
            commandLine(
                "cmake", "--build",
                layout.buildDirectory.dir("cmake-build/android-x86").get().asFile.absolutePath,
                "--parallel",
            )
        }
    }
}
```

> **Gradle 9 note:** `Project.exec {}` was removed in Gradle 9. Use `java.lang.ProcessBuilder`
> directly, or the `ExecOperations` service injected into the task class. The plugin's own
> build already does this for `clangBuildStatic` / `clangBuildDynamic`.

## Caveats

- **Apple targets only build on macOS.** `Konan.checkSysrootInstalled` succeeds on Linux
  for an Apple target because the plugin resolves the SDK path lazily, but the actual
  link step will fail. If you need iOS / tvOS / watchOS binaries, run CMake on a macOS
  host — the plugin will hand it the Xcode SDK path automatically.
- **Android NDK on Apple Silicon** needs Rosetta 2 (see [README](README.md)). The NDK
  bundled with Kotlin/Native is an Intel-only toolchain, so without Rosetta `cmake … -G
  Ninja` will fail at the first compiler invocation. `softwareupdate --install-rosetta
  --agree-to-license` fixes it (Intel Macs and Linux/Windows hosts need nothing).
- **`-fuse-ld=lld` is invalid on Apple.** The plugin's compile flags do **not** include
  `-fuse-ld=lld` for Apple targets — Apple ships its own `ld64`. Don't add it to the
  toolchain file in that case (the `useLld` flag above handles this).
- **`CMAKE_SYSROOT` and `--sysroot=…` are both set.** `clangCompileArgs` already contains
  `--sysroot=…`, so CMake's `CMAKE_SYSROOT` is redundant for clang but harmless. If a
  CMake target hard-codes its own sysroot (some `find_package` modules do), it overrides
  the flag — usually fine, just be aware.
- **CMake's iOS / tvOS / watchOS support is third-party.** The CMake upstream toolchain
  files for those platforms assume an Xcode-only flow. For a unified multi-platform
  setup across Android/Linux/Apple, point CMake at the toolchain file above (which feeds
  it the LLVM/Clang from K/N) and use an add-on like
  [ios-cmake](https://github.com/leetal/ios-cmake) for the Apple platform bits.

## What if I just need this without writing Kotlin?

If the manual extraction above is more code than you want in your build, the plugin's
own tasks (`clangBuildStatic` / `clangBuildDynamic`) already cover the simple cases:

```kotlin
clangBuildDynamic(target = KonanTarget.ANDROID_X86) {
    compileDir(file("src/main/c"))
    include(file("src/main/c/include"))
    compileArgs("-DFOO=1")            // extra clang flags
    linkArgs("-lc++_static", "-lc++abi")
}
```

For anything more complex than "compile this directory into one `.so`", reach for CMake.