import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

buildscript {
    repositories {
        maven(url = "https://plugins.gradle.org/m2/")
    }
}

plugins {
    java
    kotlin("jvm") version "1.4.30"
    kotlin("kapt") version "1.4.30"
    id("distribution")
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("net.linguica.maven-settings") version "0.5"
    id("org.hibernate.build.maven-repo-auth") version "3.0.4"
    `maven-publish`
}

group = "eu.jailbreaker.coloredchat"
version = "0.0.1"

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

fun ShadowJar.relocate(pattern: String) {
    this.relocate(pattern, "${project.name}.$pattern")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jailbreaker.eu/repositories")
    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    testImplementation("junit", "junit", "4.12")

    implementation(kotlin("stdlib"))
    compileOnly("org.spigotmc:spigot:1.16.4")
    kapt("org.spigotmc:plugin-annotations:1.2.3-SNAPSHOT")
    compileOnly("org.spigotmc:plugin-annotations:1.2.3-SNAPSHOT")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveFileName.set("ColoredChat.jar")
        relocate("kotlin")
    }

    build {
        dependsOn(shadowJar)
    }

    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}