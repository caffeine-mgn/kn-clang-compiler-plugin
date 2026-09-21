# Using `kn-clang-cli`

`kn-clang-cli` is the standalone, Gradle-free counterpart of the `pw.binom.kn-clang` Gradle plugin. It prints the Clang/LLD/archiver paths and compile flags for a given Kotlin/Native target, so you can hand them to CMake / Make / autoconf / Ninja / Bazel when building third-party C/C++ libraries (like `onnxruntime`, `LiteRT`, or anything else you want to ship as a static or shared artifact on Android/iOS/macOS/Linux from your dev box).

It uses the **same** toolchain-resolution code path as the Gradle plugin — no duplicated logic.

---

## Install

The CLI ships as an uber-jar (`kn-clang-cli.jar`) attached to every GitHub Release of this repo:

```bash
# Linux x64 / macOS arm64 — needs a JRE 17+ on PATH
curl -L -o kn-clang-cli.jar \
  https://github.com/caffeine-mgn/kn-clang-compiler-plugin/releases/latest/download/kn-clang-cli.jar

chmod +x kn-clang-cli.jar
```

Run it via:

```bash
java -jar kn-clang-cli.jar --help
```

(or `alias kn-clang-cli='java -jar /path/to/kn-clang-cli.jar'` for convenience)

> KMP-native binaries (no JRE required) for `linuxX64` / `macosArm64` / `mingwX64` are planned for the **0.1.0** release. Until then, the JVM uber-jar is the supported distribution.

---

## Quick start

```bash
# Print everything as shell vars (default format)
java -jar kn-clang-cli.jar android_arm64

# Source them straight into your shell
eval "$(java -jar kn-clang-cli.jar android_arm64 --download --format shell)"
echo "$KN_CLANG_COMPILER"     # /…/android-ndk/…/aarch64-linux-android21-clang
echo "$KN_CLANG_SYSROOT"      # /…/target-sysroot-1-android_ndk/sysroot-android-21
echo "$KN_CLANG_CFLAGS"       # -target aarch64-linux-android21 --sysroot=… -B… --gcc-toolchain=…

# Same data as JSON for programmatic use
java -jar kn-clang-cli.jar android_arm64 --format json | jq .
```

---

## CLI reference

```
kn-clang-cli <target> [options]

Options:
  -v, --version V     Kotlin/Native version (default: 2.4.20)
  -f, --format FMT    Output format: shell (default) | json
  -d, --download      Ensure the sysroot is downloaded before resolving
  -h, --help          Show this help
```

`<target>` is the standard Kotlin/Native target name (`ANDROID_ARM64`, `IOS_ARM64`, `LINUX_X64`, `WASM32`, etc. — case-insensitive). For a full list, see `KonanTarget.predefinedTargets`.

### Output formats

#### `shell` (default)

Ten `KN_CLANG_*` environment-variable lines, sourceable via `eval "$(...)"`:

| Variable              | Meaning                                                                  |
|-----------------------|--------------------------------------------------------------------------|
| `KN_CLANG_TARGET`     | `KonanTarget` enum name (`ANDROID_ARM64`, …)                             |
| `KN_CLANG_TRIPLE`     | Clang target triple (`aarch64-linux-android21`, `arm64-apple-ios15.0`, …) |
| `KN_CLANG_COMPILER`   | Absolute path to the C compiler wrapper                                  |
| `KN_CLANG_CXX_COMPILER` | Absolute path to the C++ compiler wrapper (`<clang>++`)                |
| `KN_CLANG_ARCHIVER`   | Absolute path to `llvm-ar`                                               |
| `KN_CLANG_LINKER`     | Absolute path to `ld.lld`                                                |
| `KN_CLANG_SYSROOT`    | Absolute path to the per-target sysroot                                  |
| `KN_CLANG_TOOLCHAIN`  | Absolute path to the GCC toolchain (empty on Apple)                      |
| `KN_CLANG_CFLAGS`     | Space-joined Clang compile args (`-target`, `--sysroot`, `-B`, …)       |
| `KN_CLANG_KONAN_VERSION` | The Kotlin/Native version that resolved this toolchain                |

#### `json`

Same data as a single JSON object. `cFlags` is an array (not a string). `toolchain` may be `null` (Apple targets).

---

## Real-world examples

### Building onnxruntime for Android (CMake)

`onnxruntime`'s CMake build script (`build.sh --android`) accepts `CC`, `CXX`, `CFLAGS`, `CXXFLAGS`, and `LDFLAGS`. With `kn-clang-cli` you can wire the K/N-provided NDK clang straight in:

```bash
eval "$(java -jar kn-clang-cli.jar android_arm64 --download --format shell)"

# onnxruntime wants a C++ stdlib flag on top of the base CFLAGS
CXXFLAGS="$KN_CLANG_CFLAGS -stdlib=libc++"
LDFLAGS="$KN_CLANG_CFLAGS -stdlib=libc++ -fuse-ld=lld"

./build.sh \
  --android \
  --android_api=21 \
  --android_ndk="$(dirname "$(dirname "$KN_CLANG_SYSROOT")")" \
  --android_sdk=... \
  CC="$KN_CLANG_COMPILER" \
  CXX="$KN_CLANG_CXX_COMPILER" \
  CFLAGS="$KN_CLANG_CFLAGS" \
  CXXFLAGS="$CXXFLAGS" \
  LDFLAGS="$LDFLAGS" \
  --build_dir=build/android_arm64
```

Because `$KN_CLANG_CFLAGS` already includes `-target aarch64-linux-android21 --sysroot=… -B… --gcc-toolchain=…`, onnxruntime's CMake call inherits the correct triple and sysroot without manual surgery.

### Building LiteRT for iOS arm64

```bash
eval "$(java -jar kn-clang-cli.jar ios_arm64 --download --format shell)"

# Note: -fuse-ld=lld is INVALID on Apple — Apple's default ld64 is used.
# C++ stdlib on Apple is -lc++ (not -lc++_static -lc++abi like Android).
export CC="$KN_CLANG_COMPILER"
export CXX="$KN_CLANG_CXX_COMPILER"
export CFLAGS="$KN_CLANG_CFLAGS"
export CXXFLAGS="$KN_CLANG_CFLAGS -stdlib=libc++"
export LDFLAGS="$KN_CLANG_CFLAGS -stdlib=libc++"
```

### Cross-compiling from Linux to a Linux arm64 dev board

```bash
eval "$(java -jar kn-clang-cli.jar linux_arm64 --download --format shell)"

cmake -S . -B build/linux_arm64 \
  -DCMAKE_C_COMPILER="$KN_CLANG_COMPILER" \
  -DCMAKE_CXX_COMPILER="$KN_CLANG_CXX_COMPILER" \
  -DCMAKE_AR="$KN_CLANG_ARCHIVER" \
  -DCMAKE_RANLIB=$KN_CLANG_LINKER/../llvm-ranlib \
  -DCMAKE_C_FLAGS_INIT="$KN_CLANG_CFLAGS" \
  -DCMAKE_CXX_FLAGS_INIT="$KN_CLANG_CFLAGS -stdlib=libstdc++" \
  -DCMAKE_SHARED_LINKER_FLAGS_INIT="-fuse-ld=lld" \
  -DCMAKE_SYSROOT="$KN_CLANG_SYSROOT"

cmake --build build/linux_arm64 -j
```

---

## Caveats

- **Apple targets are host-gated.** `ios_arm64`, `macos_arm64`, etc. only build final Mach-O artifacts on a **macOS host**. The CLI will still print the resolved toolchain from Linux, but `clang` itself will refuse to emit Mach-O from a non-macOS host — for those cases, run the CLI on macOS or use the CMake toolchain-file approach described in [`building-with-external-tools.md`](./building-with-external-tools.md).
- **Android on Apple Silicon needs Rosetta 2.** The Android NDK shipped with K/N is an x86_64 Mach-O binary; on an M1/M2/M3 mac, run `softwareupdate --install-rosetta --agree-to-license` once before invoking the CLI.
- **`--download` is blocking.** It downloads the sysroot (and any host toolchain) via the same code path as `knClang { downloadKonan { } }`. For Android targets this can fetch ~500 MB on first run.
- **Default `--version` is `2.4.20`.** If you want a different K/N version (e.g. `2.3.20`, `2.2.0`), pass `--version` explicitly. Make sure `~/.konan/kotlin-native-prebuilt-<host>-<version>` is installed; the CLI doesn't bootstrap K/N itself, only the per-target toolchain deps.
- **`$KN_CLANG_CFLAGS` is space-joined.** That's fine for `CC/CXX`-style env-var consumers, but if you're splitting it into separate `-D`/`-I` arrays yourself, use `--format json` to get a real array.
- **First-run on a fresh box:** running with `--download` may require accepting Apple's EULA — set `KONAN_AUTO_ACCEPT_LICENSES=1` in the environment to skip the interactive prompt.

---

## See also

- [`building-with-external-tools.md`](./building-with-external-tools.md) — generating CMake toolchain files and Make recipes programmatically via the Gradle plugin's `knClang.toolchain(target)` API.
- [`README.md`](./README.md) — quick start with the Gradle plugin.
