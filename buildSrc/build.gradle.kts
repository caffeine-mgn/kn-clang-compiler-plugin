buildscript {

    repositories {
        mavenLocal()
        mavenCentral()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    }
}

plugins {
    kotlin("jvm") version "1.6.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
}

dependencies {
    api("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    api("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.6.0")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.0")
    api("org.jetbrains.dokka:dokka-gradle-plugin:1.5.0")
    api("org.jmailen.gradle:kotlinter-gradle:3.8.0")
}
