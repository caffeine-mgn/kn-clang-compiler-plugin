import pw.binom.Versions

plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kn-clang-core"))
}

application {
    mainClass.set("pw.binom.knclang.cli.MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "pw.binom.knclang.cli.MainKt"
            attributes["Implementation-Title"] = "kn-clang-cli"
            attributes["Implementation-Version"] = project.version.toString()
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}