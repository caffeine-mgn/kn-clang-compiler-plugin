package pw.binom.kotlin.clang

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

fun Project.fileAnyWay(obj: Any?): File? =
    when (obj) {
        null -> null
        is RegularFile -> obj.asFile
        is Provider<*> -> if (obj.isPresent) fileAnyWay(obj.get()) else null
        is String -> project.file(obj)
        else -> null
    }
