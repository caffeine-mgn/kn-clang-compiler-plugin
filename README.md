# Kotlin Native's Clang Compiler Plugin

[English](README.md) | [Русский](README.ru.md)

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/pw.binom/kn-clang-compiler-plugin.svg?style=flat)](https://repo1.maven.org/maven2/pw/binom/kn-clang-compiler-plugin/)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/pw.binom.kn-clang.svg)](https://plugins.gradle.org/plugin/pw.binom.kn-clang)
[![Kotlin 2.4.20](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
[![Release to Maven Central](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/actions/workflows/release.yml/badge.svg)](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/actions/workflows/release.yml) <br><br>

This plugin gives you the Clang toolchain that ships with Kotlin/Native and lets you
compile **C and C++** sources for Kotlin/Native targets from Gradle.

Toolchain locations (LLVM binaries, sysroots, target toolchains) are resolved at runtime
from the selected Kotlin/Native distribution's `konan.properties`, so switching to another
Kotlin/Native version does not require updating the plugin.

### Installation

The plugin is published to **Maven Central** (`pw.binom:kn-clang-compiler-plugin`) and to
the **Gradle Plugin Portal** under id `pw.binom.kn-clang`. Either repository works; the
shortest form uses the default `pluginManagement.repositories` (which includes both):

```kotlin
plugins {
    id("pw.binom.kn-clang") version "0.0.10"
}
```

> **Heads-up after a new release:** the Plugin Portal catalog index is cached for ~12 hours, so a freshly-published version can take a few hours to show up in `plugins.gradle.org/plugin/...` listings even though the artifact itself is already resolvable via `mavenCentral()` and via `gradlePluginPortal()` (which uses the live repo, not the cached index).

Make sure `mavenCentral()` is in `pluginManagement.repositories` (it is by default in new
Gradle projects). If you override `pluginManagement.repositories`, add it there:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Or via the version catalog (`gradle/libs.versions.toml`):

```toml
[versions]
kn-clang = "0.0.10"

[plugins]
kn-clang = { id = "pw.binom.kn-clang", version.ref = "kn-clang" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kn.clang)
}
```

### Standalone CLI (`kn-clang-cli`)

Every GitHub release also ships a standalone CLI uber-jar (`kn-clang-cli.jar`) — the same
toolchain resolution that the plugin uses, callable from shell scripts and external
build systems (CMake, Make, autotools, Bazel). Useful when you want to cross-compile a
large third-party C/C++ project (onnxruntime, LiteRT, …) on your dev machine without
pulling in Gradle.

```sh
# Download from the GitHub release
curl -L -o kn-clang-cli.jar \
  https://github.com/caffeine-mgn/kn-clang-compiler-plugin/releases/latest/download/kn-clang-cli.jar

# Resolve the toolchain for a target (requires JRE 17+)
java -jar kn-clang-cli.jar android_arm64 --download --format shell
# KN_CLANG_COMPILER=/home/.../llvm-21-x86_64-linux-essentials-116/bin/clang
# KN_CLANG_CXX_COMPILER=/home/.../bin/clang++
# KN_CLANG_ARCHIVER=/home/.../bin/llvm-ar
# KN_CLANG_LINKER=/home/.../bin/lld
# KN_CLANG_SYSROOT=/home/.../target-sysroot-1-android_ndk/android-21/arch-arm64
# KN_CLANG_CFLAGS=-O2 -target aarch64-unknown-linux-android -fexceptions …

eval "$(java -jar kn-clang-cli.jar android_arm64 --format shell)"
"$KN_CLANG_CXX_COMPILER" $KN_CLANG_CFLAGS -shared -o libfoo.so foo.cpp
```

The CLI prints the same `KnClangToolchain` data the plugin's `knClang.toolchain(target)`
returns, in a shell- or JSON-friendly format. See
[`building-with-external-tools.md`](building-with-external-tools.md) for the full
CMake/Make recipes. KMP-native builds of the CLI (linuxX64 / macosArm64 / mingwX64, no
JRE required) are coming in 0.1.0.

### Example

Project layout:

```
myproj/
├── build.gradle.kts
└── src/main/c/
    ├── hello.c
    └── hello.h
```

`src/main/c/hello.h`:

```c
#pragma once
int add(int a, int b);
```

`src/main/c/hello.c`:

```c
#include "hello.h"

int add(int a, int b) { return a + b; }
```

`build.gradle.kts`:

```kotlin
import pw.binom.kotlin.clang.*

plugins {
    id("pw.binom.kn-clang") version "0.0.10"
}

knClang {
    konanVersion.set("2.4.20")
}

clangBuildDynamic {
    compileDir(file("src/main/c"))
    include(file("src/main/c"))
    optimizationLevel(2)
}
```

Run:

```sh
./gradlew buildDynamicLinuxX64
```

The first run downloads the requested Kotlin/Native distribution into `~/.konan` and the
target toolchain/sysroot on demand; subsequent builds are incremental. The output is a
platform-native shared library (`.so` on Linux/Android, `.dylib` on macOS, `.dll` on
MinGW).

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
    id("pw.binom.kn-clang") version "0.0.10"
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
    id("pw.binom.kn-clang") version "0.0.10"
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

### Building with CMake / external build systems

`clangBuildStatic` / `clangBuildDynamic` cover the simple "compile this directory into one
library" case. For anything more involved — multi-file projects, generated code, mixed
C/C++/Objective-C, CMake/autoconf-based third-party libraries — it's usually easier to let
the project's own build system orchestrate the build and just give it the right toolchain.

The plugin exposes this directly through `knClang.toolchain(target)` — pass a
`KonanTarget`, get back a `KnClangToolchain` with `compiler`, `cxxCompiler`, `archiver`,
`linker`, `sysRoot`, `triple` and ready-to-use `cFlags`. The target's sysroot is downloaded
on the first call. You can feed the result straight into a CMake toolchain file or any
other build system that consumes a C/C++ toolchain.

```kotlin
val androidX86 = knClang.toolchain(KonanTarget.ANDROID_X86)
val flags      = androidX86.cFlags.joinToString(" ")
```

See [**building-with-external-tools.md**](building-with-external-tools.md) for the full
example (resolving the toolchain, generating a `.cmake` toolchain file, and invoking
`cmake -S … -B … -DCMAKE_TOOLCHAIN_FILE=…`).

### Examples

[github.com/klua/build.gradle.kts](https://github.com/caffeine-mgn/klua/blob/main/build.gradle.kts)
