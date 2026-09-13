package pw.binom.kotlin.clang

import org.gradle.api.provider.Property

abstract class KnClangExtension {
    abstract val konanVersion: Property<String>

    init {
        konanVersion.convention(KotlinVersions.V2_4_20.toString())
    }
}