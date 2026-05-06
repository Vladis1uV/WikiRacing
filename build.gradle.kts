plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("com.gradleup.shadow") version "9.4.1"
    application
}

group = "dev.vladislav.wikirace"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
    testImplementation("org.junit.platform:junit-platform-console:1.9.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.vladislav.wikirace.MainKt")
}

tasks.getByName<JavaExec>("run") {
    standardInput = System.`in`
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/build/**")
        exclude("**/test/**")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.test {
    filter { excludeTestsMatching("CliTest") }
}

tasks.register<Test>("testCli") {
    description = "Runs the CLI integration tests against the shadow JAR."
    group = "verification"
    dependsOn("shadowJar")
    filter { includeTestsMatching("CliTest") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
}

tasks.register("installCli") {
    description = "Builds the fat JAR and installs the ./wikiRacer wrapper script."
    group = "application"
    dependsOn("shadowJar")
    doLast {
        val script = file("wikiRacer")
        script.writeText(
            "#!/bin/sh\nexec java -jar \"\$(dirname \"\$0\")/build/libs/${project.name}-${project.version}-all.jar\" \"\$@\"\n",
        )
        script.setExecutable(true)
    }
}
