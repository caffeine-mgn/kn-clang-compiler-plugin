package pw.binom.kotlin.clang

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

fun Project.fileAnyWay(obj:Any?): File? {
    obj?:return null
    if (obj is RegularFile){
        return obj.asFile
    }
    if (obj is Provider<*>){
        if (!obj.isPresent) {
            return null
        }
        return fileAnyWay(obj.get())
    }
    if (obj is String){
        project.file(obj)
    }
    return null
}