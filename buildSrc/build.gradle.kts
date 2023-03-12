buildscript {

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
    }
}

plugins {
    kotlin("jvm") version "1.8.10"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://repo.binom.pw")
    maven(url = "https://plugins.gradle.org/m2/")
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.10")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
    api("org.jetbrains.dokka:dokka-gradle-plugin:1.6.21")
    api("pw.binom:binom-publish:0.1.5")
}
