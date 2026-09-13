import pw.binom.plugins.PublishInfo

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.2.1"
}

apply {
    plugin<org.jetbrains.dokka.gradle.DokkaPlugin>()
}

allprojects {
    version = System.getenv("GITHUB_REF_NAME") ?: "1.0.0-SNAPSHOT"
    group = "pw.binom"

    repositories {
        mavenLocal()
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

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn("dokkaJavadoc")
    archiveClassifier.set("javadoc")
    from("dokkaJavadoc")
}

tasks {
    test {
        useJUnitPlatform()
    }
}
publishing {
    publications {
        val sources = tasks.getByName("kotlinSourcesJar")
        val docs = tasks.getByName("javadocJar")
        create<MavenPublication>("KnClang") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["kotlin"])
            artifact(sources)
            artifact(docs)
        }
    }
}

apply<pw.binom.publish.plugins.PrepareProject>()
