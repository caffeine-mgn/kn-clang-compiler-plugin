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

allprojects {
    version = System.getenv("GITHUB_REF_NAME")?.removePrefix("v") ?: (findProperty("version") as String? ?: "1.0.0-SNAPSHOT")
    group = "pw.binom"

    repositories {
        mavenCentral()
    }
}

dependencies {
    api(gradleApi())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:${pw.binom.Versions.KOTLIN_VERSION}")
    implementation("org.apache.commons:commons-compress:1.21")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    website = PublishInfo.HTTP_PATH_TO_PROJECT
    vcsUrl = PublishInfo.GIT_PATH_TO_PROJECT
    description = PublishInfo.DESCRIPTION
    plugins {
        create("kn-clang") {
            id = "kn-clang"
            implementationClass = "pw.binom.kotlin.clang.ClangPlugin"
            description = "Kotlin-Native Clang"
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

// The plugin marker publication uses `kn-clang` as its group id, which we are not
// authorized to publish under on Maven Central. Drop it here so vanniktech only
// publishes `pluginMaven`. The marker is still published to the Gradle Plugin Portal
// by `com.gradle.plugin-publish`.
afterEvaluate {
    publishing.publications.removeIf { it.name.endsWith("PluginMarkerMaven") }
    tasks.matching { it.name.contains("PluginMarker") && (it.name.startsWith("publish") || it.name.startsWith("sign") || it.name.startsWith("generate")) }
        .configureEach { this.enabled = false }
}

tasks {
    test {
        useJUnitPlatform()
    }
}
