buildscript {
    val kotlinVersion = properties.get("kotlin.version") as String
    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

plugins {
    kotlin("jvm") version "1.8.21"
}

val kotlinVersion = project.property("kotlin.version") as String

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://repo.binom.pw")
    maven(url = "https://plugins.gradle.org/m2/")
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
//    api("org.jetbrains.dokka:dokka-gradle-plugin:1.6.21")
    api("pw.binom:binom-publish:0.1.8")
}
