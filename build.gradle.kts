import pw.binom.getGitBranch
import pw.binom.plugins.PublishInfo

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("org.jmailen.kotlinter")
    id("com.gradle.plugin-publish") version "0.16.0"
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
// tasks {
//    val kotlinSourcesJar by getting {
//    }
// //    val sourcesJar by creating(Jar::class) {
// //        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
// //        archiveClassifier.set("sources")
// //        from(kotlin.sourceSets["main"].kotlin)
// // //        from(sourceSets["main"].allSource)
// //    }
// //    artifacts {
// ////        this.sourceArtifacts(sourcesJar)
// //        add("archives", kotlinSourcesJar)
// ////        add("archives", )
// ////        add("archives", javadocJar)
// //    }
// }

dependencies {
    api(gradleApi())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:${pw.binom.Versions.KOTLIN_VERSION}")
    implementation("org.apache.commons:commons-compress:1.21")
}

//apply<pw.binom.plugins.DocsPlugin>()

//kotlinter {
//    indentSize = 4
//    disabledRules = arrayOf("no-wildcard-imports")
//}
gradlePlugin {
    plugins {
        create("kn-clang") {
            id = "kn-clang"
            implementationClass = "pw.binom.kotlin.clang.ClangPlugin"
            description = "Kotlin-Native Clang"
            isAutomatedPublishing = false
        }
    }
}
pluginBundle {
    website = PublishInfo.HTTP_PATH_TO_PROJECT
    vcsUrl = PublishInfo.GIT_PATH_TO_PROJECT
    description = PublishInfo.DESCRIPTION
    tags = listOf("kotlin", "clang", "konan")
}

tasks {
    val javadocJar by creating(Jar::class) {
        dependsOn("dokkaJavadoc")
        archiveClassifier.set("javadoc")
        from(javadoc)
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
