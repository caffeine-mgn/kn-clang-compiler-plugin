# Kotlin Native's Clang Compiler Plugin

[English](README.md) | [Русский](README.ru.md)

[![Лицензия GitHub](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/pw.binom/kn-clang-compiler-plugin.svg?style=flat)](https://repo1.maven.org/maven2/pw/binom/kn-clang-compiler-plugin/)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/pw.binom.kn-clang.svg)](https://plugins.gradle.org/plugin/pw.binom.kn-clang)
[![Kotlin 2.4.20](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?style=flat&logo=kotlin)](http://kotlinlang.org)
[![Релиз в Maven Central](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/actions/workflows/release.yml/badge.svg)](https://github.com/caffeine-mgn/kn-clang-compiler-plugin/actions/workflows/release.yml) <br><br>

Плагин даёт доступ к Clang-тулчейну, поставляемому с Kotlin/Native, и позволяет
собирать **C и C++** исходники под таргеты Kotlin/Native прямо из Gradle.

Расположение тулчейнов (бинарники LLVM, sysroot-ы, таргет-тулчейны) определяется
в рантайме из `konan.properties` выбранной версии Kotlin/Native — переключение
на другую версию не требует обновления плагина.

### Установка

Плагин публикуется в **Maven Central** (`pw.binom:kn-clang-compiler-plugin`) и в
**Gradle Plugin Portal** под id `pw.binom.kn-clang`. Любой из репозиториев подойдёт;
самая короткая форма использует дефолтный `pluginManagement.repositories` (включает оба):

```kotlin
plugins {
    id("pw.binom.kn-clang") version "0.0.5"
}
```

> **После нового релиза:** каталог Plugin Portal кэширует индекс примерно на 12 часов, поэтому
> только что опубликованная версия может появиться в `plugins.gradle.org/plugin/...` не сразу —
> хотя сам артефакт уже доступен через `mavenCentral()` и `gradlePluginPortal()` (который
> использует живой репозиторий, а не кэшированный индекс).

Убедитесь, что `mavenCentral()` указан в `pluginManagement.repositories`
(в новых проектах он там по умолчанию). Если переопределяете `pluginManagement.repositories`,
добавьте его туда:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Или через version-catalog (`gradle/libs.versions.toml`):

```toml
[versions]
kn-clang = "0.0.5"

[plugins]
kn-clang = { id = "pw.binom.kn-clang", version.ref = "kn-clang" }
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kn.clang)
}
```

### Пример

Структура проекта:

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
    id("pw.binom.kn-clang") version "0.0.5"
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

Запуск:

```sh
./gradlew buildDynamicLinuxX64
```

Первый прогон скачивает выбранный дистрибутив Kotlin/Native в `~/.konan` и тулчейн/sysroot
для таргета по необходимости; последующие сборки инкрементальны. На выходе — нативная
динамическая библиотека (`.so` на Linux/Android, `.dylib` на macOS, `.dll` на MinGW).

### Поддерживаемые таргеты

| Таргет | Семейство |
|--------|-----------|
| `linux_x64`, `linux_arm64`, `linux_arm32_hfp` | Linux |
| `mingw_x64` | Windows |
| `android_arm32`, `android_arm64`, `android_x86`, `android_x64` | Android |
| `macos_x64`, `macos_arm64` | macOS |
| `ios_arm64`, `ios_x64`, `ios_simulator_arm64` | iOS |
| `tvos_arm64`, `tvos_x64`, `tvos_simulator_arm64` | tvOS |
| `watchos_arm32`, `watchos_arm64`, `watchos_x64`, `watchos_simulator_arm64` | watchOS |

Это полный набор таргетов Kotlin/Native. Какие из них реально могут быть собраны,
зависит от хоста:

| Хост | Apple-таргеты | Linux / MinGW / Android |
|------|:---:|:---:|
| **macOS** (Intel или Apple Silicon) | ✅ нативно | ✅ кросс-компиляция |
| Linux | ❌ | ✅ |
| Windows | ❌ | ✅ |

- **Apple-таргеты (macOS/iOS/tvOS/watchOS) можно собрать только на хосте с macOS.**
  Это ограничение Kotlin/Native — он отказывается собирать финальные Apple-бинарники
  на Linux/Windows. На macOS Apple SDK берётся из установленного Xcode (так же, как
  делает сам Kotlin/Native).
- Всё остальное собирается кросс-компиляцией: например, Linux и MinGW-бинарники из
  macOS, Android из Linux/macOS и так далее.
- **Android на Apple Silicon** требует одноразовой установки [Rosetta 2](https://support.apple.com/en-us/102527),
  потому что Android NDK, поставляемый с Kotlin/Native, — это Intel-тулчейн:
  ```sh
  softwareupdate --install-rosetta --agree-to-license
  ```
  Intel Mac-ам и хостам с Linux/Windows ничего дополнительно не нужно.

### Выбор версии Kotlin/Native

Плагин собирает тулчейном той версии Kotlin/Native, которую вы указали.
По умолчанию — `2.4.20`; подходит любой 2.x дистрибутив Kotlin/Native со стандартным
`konan.properties` (проверено на `2.2.0` и `2.4.20`). Заранее ставить Kotlin/Native
не нужно — задачи `downloadKonan` / `downloadKonanToolchain<Target>` сами скачают
его в `~/.konan` по требованию.

```kotlin
plugins {
    id("pw.binom.kn-clang") version "0.0.5"
}

knClang {
    konanVersion.set("2.4.20")
}
```

Конкретная build-задача может переопределить версию через `konanVersion.set(...)`
внутри своего блока конфигурации.

### Как использовать

```kotlin
import pw.binom.kotlin.clang.*

plugins {
    id("pw.binom.kn-clang") version "0.0.5"
}

knClang {
    konanVersion.set("2.4.20")
}

// Статическая библиотека (libnative.a)
clangBuildStatic {
    compileDir(file("src/main/c"))
    include(file("src/main/c/include"))
    optimizationLevel(2)
}

// Динамическая библиотека (.so / .dylib / .dll)
clangBuildDynamic {
    compileDir(file("src/main/c"))
    include(file("src/main/c/include"))
}
```

- `compileDir(dir)` — скомпилировать все `.c` / `.cpp` в каталоге (рекурсивно).
- `compileFile(file)` — скомпилировать один файл.
- `include(...)` — добавить каталоги для поиска заголовков.
- `compileArgs(...)` — дополнительные флаги clang.
- `linkArgs(...)` — дополнительные флаги линкера (только для динамической сборки;
  статические `.a` собираются через `llvm-ar`).
- `optimizationLevel(0..3)`, `debugEnabled(true)`, `multiThread = false`.

Если в C++ используется стандартная библиотека, линкуйте её явно через `linkArgs(...)`;
флаг зависит от семейства таргета:

| Семейство | флаг |
|-----------|------|
| Linux, MinGW | `-lstdc++` |
| macOS, iOS, tvOS, watchOS | `-lc++` |
| Android | `-lc++_static -lc++abi` |

`name` (по умолчанию `native`) задаёт имя артефакта, `target` выбирает таргет сборки:

```kotlin
import org.jetbrains.kotlin.konan.target.KonanTarget

clangBuildStatic(name = "mylib", target = KonanTarget.ANDROID_ARM64) {
    compileDir(file("src/main/c"))
}
```

### Примеры в реальных проектах

[github.com/klua/build.gradle.kts](https://github.com/caffeine-mgn/klua/blob/main/build.gradle.kts)
