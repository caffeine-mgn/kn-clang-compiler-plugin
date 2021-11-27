plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

apply {
    plugin(pw.binom.plugins.BinomPublishPlugin::class.java)
}

allprojects {
    version = pw.binom.Versions.LIB_VERSION
    group = "pw.binom"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

dependencies {
    api(gradleApi())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:${pw.binom.Versions.KOTLIN_VERSION}")
}

apply<pw.binom.plugins.DocsPlugin>()

gradlePlugin {
    plugins {
        create("kn-clang") {
            id = "kn-clang"
            implementationClass = "pw.binom.kotlin.clang.ClangPlugin"
            description = "Kotlin-Native Clang"
        }
    }
}