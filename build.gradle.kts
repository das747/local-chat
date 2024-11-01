plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "1.8.0"
    application
}

group = "com.das747"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.6")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

val systemProps by extra {
    listOf(
        "localChat.logfile",
        "localChat.server"
    )
}

application {
    mainClass.set("com.das747.localchat.MainKt")
    applicationDefaultJvmArgs = properties.filterKeys { it in systemProps }.map { "-D${it.key}=${it.value}" }
    applicationDefaultJvmArgs.forEach { println(it) }
}

tasks.run.configure {
    this.standardInput = System.`in`
}