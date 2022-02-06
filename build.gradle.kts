import pw.binom.getGitBranch

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    id("org.jmailen.kotlinter")
}

apply {
    plugin(pw.binom.plugins.BinomPublishPlugin::class.java)
}

allprojects {
    val branch = getGitBranch()
    version = if (branch == "main" || branch == "master")
        pw.binom.Versions.LIB_VERSION
    else
        "${pw.binom.Versions.LIB_VERSION}-SNAPSHOT"
    group = "pw.binom"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}
tasks {
    val kotlinSourcesJar by getting {
    }
//    val sourcesJar by creating(Jar::class) {
//        dependsOn(JavaPlugin.CLASSES_TASK_NAME)
//        archiveClassifier.set("sources")
//        from(kotlin.sourceSets["main"].kotlin)
// //        from(sourceSets["main"].allSource)
//    }
    artifacts {
//        this.sourceArtifacts(sourcesJar)
        add("archives", kotlinSourcesJar)
//        add("archives", )
//        add("archives", javadocJar)
    }
}

dependencies {
    api(gradleApi())
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:${pw.binom.Versions.KOTLIN_VERSION}")
}

apply<pw.binom.plugins.DocsPlugin>()

kotlinter {
    indentSize = 4
    disabledRules = arrayOf("no-wildcard-imports")
}
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

tasks {
    val dokkaJavadoc by getting
    val javadocJar by creating(Jar::class) {
        dependsOn(dokkaJavadoc)
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
tasks {
    val compileKotlin by getting {
        dependsOn("lintKotlinMain")
    }
}
