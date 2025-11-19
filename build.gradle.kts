plugins {
    kotlin("jvm") version "2.2.20"
    alias(libs.plugins.kotlin.serialization)
}

group = "ai.koog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.koog.agents)
    implementation(libs.grazie.executor)
    implementation(libs.grazie.models)
    implementation(libs.logback.classic)
    // Coroutines for desktop Swing UI (provides Dispatchers.Main for Swing)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // YAML parsing for Nemory
    implementation("com.charleskorn.kaml:kaml:0.55.0")
    // SQL parsing
    implementation("com.github.jsqlparser:jsqlparser:4.9")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("run01") {
    group = "application"
    description = "Run 01-PromptTask.kt"
    mainClass.set("ai.koog.workshop.intro._01_PromptTaskKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("run02") {
    group = "application"
    description = "Run 02-PromptExecutorTask.kt"
    mainClass.set("ai.koog.workshop.intro._02_PromptExecutorTaskKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("run03") {
    group = "application"
    description = "Run 03-ToolTask.kt"
    mainClass.set("ai.koog.workshop.intro._03_ToolTaskKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("run04") {
    group = "application"
    description = "Run 04-ToolSetTask.kt"
    mainClass.set("ai.koog.workshop.intro._04_ToolSetTaskKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("run05") {
    group = "application"
    description = "Run 05-ToolRegistryTask.kt"
    mainClass.set("ai.koog.workshop.intro._05_ToolRegistryTaskKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("run06") {
    group = "application"
    description = "Run 06-SimpleAgent.kt"
    mainClass.set("ai.koog.workshop.intro._06_SimpleAgentKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Run the Query Explainer & Tutor CLI
tasks.register<JavaExec>("runQueryTutor") {
    group = "application"
    description = "Run QueryTutorCli (SQL Query Explainer & Tutor)"
    mainClass.set("ai.koog.workshop.cli.QueryTutorCliKt")
    classpath = sourceSets["main"].runtimeClasspath
    // Pass through all args to the program
    standardInput = System.`in`
}

// Run the Query Tutor simple UI
tasks.register<JavaExec>("runQueryTutorUI") {
    group = "application"
    description = "Run QueryTutorUi (Simple Swing UI)"
    mainClass.set("ai.koog.workshop.cli.QueryTutorUiKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}