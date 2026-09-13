# Kotlin Native's Clang Compiler Plugin

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin 2.4.20](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
[![Gradle build](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/actions/workflows/publish.yml/badge.svg) ](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/actions/workflows/publish.yml) <br><br>

This plugin gives you the Clang toolchain that ships with Kotlin/Native and lets you
compile **C and C++** sources for Kotlin/Native targets from Gradle.

Toolchain locations (LLVM binaries, sysroots, target toolchains) are resolved at runtime
from the selected Kotlin/Native distribution's `konan.properties`, so switching to another
Kotlin/Native version does not require updating the plugin.

### Supported targets

| Target | Family |
|--------|--------|
| `linux_x64`, `linux_arm64`, `linux_arm32_hfp` | Linux |
| `mingw_x64` | Windows |
| `android_arm32`, `android_arm64`, `android_x86`, `android_x64` | Android |
| `macos_x64`, `macos_arm64` | macOS |
| `ios_arm64`, `ios_x64`, `ios_simulator_arm64` | iOS |
| `tvos_arm64`, `tvos_x64`, `tvos_simulator_arm64` | tvOS |
| `watchos_arm32`, `watchos_arm64`, `watchos_x64`, `watchos_simulator_arm64` | watchOS |

This is the full Kotlin/Native target set. Which of them a build can actually produce
depends on the host:

| Host | Apple targets | Linux / MinGW / Android |
|------|:---:|:---:|
| **macOS** (Intel or Apple Silicon) | ✅ native | ✅ cross-compiled |
| Linux | ❌ | ✅ |
| Windows | ❌ | ✅ |

- **Apple targets (macOS/iOS/tvOS/watchOS) can only be built on a macOS host.** This is a
  Kotlin/Native limitation — it refuses final Apple binaries on Linux/Windows. On macOS the
  Apple SDKs are taken from the installed Xcode (just like Kotlin/Native itself does).
- Everything else can be cross-compiled: e.g. Linux and MinGW binaries from macOS, Android
  from Linux/macOS, and so on.
- **Android on Apple Silicon** requires a one-time [Rosetta 2](https://support.apple.com/en-us/102527)
  install, because the Android NDK bundled with Kotlin/Native is an Intel-only toolchain:
  ```sh
  softwareupdate --install-rosetta --agree-to-license
  ```
  Intel Macs and Linux/Windows hosts need nothing extra.

### Choosing the Kotlin/Native version

The plugin compiles with the toolchain of the Kotlin/Native version you select.
The default is `2.4.20`; any Kotlin/Native 2.x distribution exposing the standard
`konan.properties` works (verified on `2.2.0` and `2.4.20`). There is no need to have
Kotlin/Native installed beforehand — the `downloadKonan` / `downloadKonanToolchain<Target>`
tasks fetch it into `~/.konan` on demand.

```kotlin
plugins {
    id("kn-clang")
}

knClang {
    konanVersion.set("2.4.20")
}
```

A single build task can override it with `konanVersion.set(...)` inside its configuration.

### How to use

```kotlin
import pw.binom.kotlin.clang.*

plugins {
    id("kn-clang")
}

knClang {
    konanVersion.set("2.4.20")
}

// Static library (libnative.a)
clangBuildStatic {
    compileDir(file("src/main/c"))
    include(file("src/main/c/include"))
    optimizationLevel(2)
}

// Shared library (.so / .dylib / .dll)
clangBuildDynamic {
    compileDir(file("src/main/c"))
    include(file("src/main/c/include"))
}
```

- `compileDir(dir)` — compile every `.c` / `.cpp` under a directory (recursively).
- `compileFile(file)` — compile a single source.
- `include(...)` — add header search directories.
- `compileArgs(...)` — pass extra clang flags.
- `linkArgs(...)` — pass extra link flags (dynamic builds only; static archives are made with `llvm-ar`).
- `optimizationLevel(0..3)`, `debugEnabled(true)`, `multiThread = false`.

When C++ code uses the standard library, link it explicitly with `linkArgs(...)`; the flag
depends on the target family:

| Family | flag |
|--------|------|
| Linux, MinGW | `-lstdc++` |
| macOS, iOS, tvOS, watchOS | `-lc++` |
| Android | `-lc++_static -lc++abi` |

`name` (default `native`) controls the artifact name, and `target` selects the build target:

```kotlin
import org.jetbrains.kotlin.konan.target.KonanTarget

clangBuildStatic(name = "mylib", target = KonanTarget.ANDROID_ARM64) {
    compileDir(file("src/main/c"))
}
```

### Examples

[github.com/klua/build.gradle.kts](https://github.com/caffeine-mgn/klua/blob/main/build.gradle.kts)
