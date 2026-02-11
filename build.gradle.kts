import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("io.izzel.taboolib") version "2.0.28"
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("com.google.devtools.ksp") version "1.9.21-1.0.15"
}

taboolib {
    description {
        name("MultiCurrencyEconomy")
        desc("多货币经济插件")
        contributors {
            name("WCPE")
        }
        dependencies {
            name("CoreLib").optional(false)
            name("PlaceholderAPI").optional(true)

        }
    }
    env {
        install(Basic)
        install(Bukkit)
        install(BukkitUtil)
        install(Kether)
        install(I18n)
    }
    version { taboolib = "6.2.4-fa94b997" }
}


repositories {
    mavenLocal()
    maven("https://maven.wcpe.top/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    mavenCentral()
}

dependencies {
    taboo(project(":api"))
    compileOnly("ink.ptms.core:v12004:12004:mapped")
    compileOnly("ink.ptms.core:v12004:12004:universal")

    compileOnly("top.wcpe.mc.plugin.corelib:corelib-api:1.0.0-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")

    compileOnly("com.easy-query:sql-core:3.1.82")
    compileOnly("com.easy-query:sql-mysql:3.1.82")
    compileOnly("com.easy-query:sql-api-proxy:3.1.82")
    ksp("com.easy-query:sql-ksp-processor:3.1.82")

    compileOnly(kotlin("stdlib"))
    compileOnly(fileTree("libs"))

    testImplementation(kotlin("test"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs = listOf("-Xjvm-default=all")
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
