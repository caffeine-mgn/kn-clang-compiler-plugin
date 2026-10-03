import pw.binom.Versions
import pw.binom.plugins.PublishInfo

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.gradle.plugin-publish") version "2.2.1"
    id("com.vanniktech.maven.publish") version "0.33.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // :kn-clang's jar bundles :kn-clang-core's compiled classes (see the jar
    // task below), so :kn-clang-core is compileOnly and must NOT appear as a
    // resolvable `pw.binom:kn-clang-core` dependency in the POM/Gradle
    // metadata — that module is internal and never published. Its runtime
    // dependencies are republished here instead, so the fat-jar has a complete
    // classpath for consumers.
    compileOnly(project(":kn-clang-core"))
    compileOnly(gradleApi())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.KOTLIN_VERSION}")
    api("org.apache.commons:commons-compress:1.21")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.KOTLIN_VERSION}")
    testImplementation(project(":kn-clang-core"))
}

tasks.named<Jar>("jar") {
    // Fat-jar: absorb :kn-clang-core's compiled classes and also republish its
    // runtime dependencies (kotlin-gradle-plugin, commons-compress) so consumers
    // get a complete classpath without a separate :kn-clang-core artifact.
    from(project(":kn-clang-core").tasks.named<Jar>("jar").map { zipTree(it.outputs.files.singleFile) }) {
        exclude { details ->
            details.file.name.startsWith("META-INF") &&
                (details.file.name.endsWith(".kotlin_module") ||
                 details.file.name.endsWith(".SF") ||
                 details.file.name.endsWith(".RSA"))
        }
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isZip64 = true
}

tasks {
    test {
        useJUnitPlatform()
    }
}

gradlePlugin {
    website = PublishInfo.HTTP_PATH_TO_PROJECT
    vcsUrl = PublishInfo.GIT_PATH_TO_PROJECT
    description = PublishInfo.DESCRIPTION
    plugins {
        create("pw.binom.kn-clang") {
            id = "pw.binom.kn-clang"
            implementationClass = "pw.binom.kotlin.clang.ClangPlugin"
            displayName = "Kotlin-Native Clang"
            description = "Plugin for compile C and C++ using Konan's Clang"
            tags = listOf("c", "c++", "clang", "konan", "kotlin-native")
        }
    }
}

if (findProperty("signingUseGpg") == "true") {
    signing {
        useGpgCmd()
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates(
        groupId = "pw.binom",
        artifactId = "kn-clang-compiler-plugin",
        version = project.version.toString()
    )
    pom {
        name.set(PublishInfo.NAME)
        description.set(PublishInfo.DESCRIPTION)
        url.set(PublishInfo.HTTP_PATH_TO_PROJECT)
        scm {
            connection.set(PublishInfo.GIT_PATH_TO_PROJECT)
            url.set(PublishInfo.HTTP_PATH_TO_PROJECT)
        }
        developers {
            developer {
                id.set("subochev")
                name.set("Anton Subochev")
                email.set("caffeine.mgn@gmail.com")
            }
        }
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
    }
}