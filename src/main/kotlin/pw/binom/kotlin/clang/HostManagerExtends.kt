package pw.binom.kotlin.clang

import org.jetbrains.kotlin.konan.target.HostManager

val HostManager.Companion.pathSeparator
    get() = if (hostIsMingw) {
        ';'
    } else {
        ':'
    }