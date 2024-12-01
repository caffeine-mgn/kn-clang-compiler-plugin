buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
}

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.gmazzo.buildconfig") version "3.0.3"
}

val kotlinVersion = kotlin.coreLibrariesVersion

buildConfig {
    packageName(project.group.toString())
    buildConfigField("String", "KOTLIN_VERSION", "\"$kotlinVersion\"")
}

repositories {
    mavenLocal()
    maven(url = "https://repo.binom.pw")
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("pw.binom:binom-publish:0.1.20")
}
