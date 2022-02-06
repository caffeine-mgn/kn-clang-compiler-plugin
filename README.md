# Kotlin Native's Clang Compiler Plugin

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Kotlin 1.6.10](https://img.shields.io/badge/Kotlin-1.6.10-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
[![Gradle build](https://github.com/caffeine-mgn/bitarray/actions/workflows/publish.yml/badge.svg) ](https://github.com/caffeine-mgn/bitarray/actions/workflows/publish.yml) <br><br>

This plugin makes possible to use konon clang compiler.

### Support targets:
|Target Name| Host compatibility |
|----|--------------------|
|linux_x64| Windows, Linux x64 |
|macos_x64| Macos              |
|mingw_x64,mingw| Windows, Linux x64 |
|mingw_x86| Windows, Linux x64 |
|linux_mips32| Linux x64          |
|linux_mipsel32| Linux x64          |
|linux_arm32_hfp,raspberrypi| Windows, Linux x64 |
|linux_arm64| Windows, Linux x64 |
|android_arm32| Windows, Linux x64 |
|android_arm64| Windows, Linux x64 |
|android_x86| Windows, Linux x64 |
|android_x64| Windows, Linux x64 |
|wasm32| Windows, Linux x64 |

### How to use
#### Examples:
[github.com/klua/build.gradle.kts](https://github.com/caffeine-mgn/klua/blob/main/build.gradle.kts)