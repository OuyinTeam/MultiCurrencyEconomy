plugins {
    java
    kotlin("jvm")
    `maven-publish`
    id("io.izzel.taboolib") version "2.0.28"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}
taboolib { subproject = true }

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")
}

repositories {
    mavenLocal()
    maven("https://maven.wcpe.top/repository/maven-public/")
    mavenCentral()
}


publishing {
    repositories {
        maven {
            credentials {
                username = project.findProperty("username").toString()
                password = project.findProperty("password").toString()
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
            val releasesRepoUrl = uri("https://maven.wcpe.top/repository/maven-releases/")
            val snapshotsRepoUrl = uri("https://maven.wcpe.top/repository/maven-snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        }
        mavenLocal()
    }
    publications {
        // API 发布配置
        create<MavenPublication>("api") {
            groupId = project.group.toString()
            artifactId = "${rootProject.name}-${project.name}".lowercase()
            version = "${project.version}"
            from(components["java"])
            println("> Apply \"$groupId:$artifactId:$version\"")
        }
    }
}
