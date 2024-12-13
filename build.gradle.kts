import com.bmuschko.gradle.docker.tasks.container.*
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.bmuschko.docker-remote-api") version "9.3.3"
}

group = "com.wallet.levyaton"
version = "0.0.1-SNAPSHOT"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.bitcoinj:bitcoinj-core:0.17-beta1")
    implementation ("org.jetbrains.kotlin:kotlin-stdlib")
    implementation ("org.bouncycastle:bcprov-jdk15on:1.70" )// For cryptographic functions
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xcontext-receivers")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Variables for Docker containers
val bitcoindImage = "ruimarinho/bitcoin-core:latest"
val bitcoindContainerName = "bitcoind"
val bitcoinDataDir = project.file("data/bitcoin").absolutePath

val lndImage = "lightninglabs/lnd:latest"
val lndContainerName = "lnd"
val lndDataDir = project.file("data/lnd").absolutePath

// Pull Docker images
tasks.register<DockerPullImage>("pullBitcoindImage") {
    image.set(bitcoindImage)
}

tasks.register<DockerPullImage>("pullLndImage") {
    image.set(lndImage)
}

// Create Bitcoind container
val createBitcoindContainer = tasks.register<DockerCreateContainer>("createBitcoindContainer") {
    dependsOn("pullBitcoindImage")
    imageId.set(bitcoindImage)
    containerName.set(bitcoindContainerName)

    // Volume mapping
    hostConfig.binds.set(
        mapOf(
            bitcoinDataDir to "/bitcoin/.bitcoin"
        )
    )

    // Port mappings
    hostConfig.portBindings.set(
        listOf(
            "8333:8333",
            "8332:8332"
        )
    )

    // Command line arguments
    cmd.set(listOf("-printtoconsole", "-server", "-regtest=1"))
}

// Start Bitcoind container
val startBitcoindContainer = tasks.register<DockerStartContainer>("startBitcoindContainer") {
    dependsOn(createBitcoindContainer)
    containerId.set(createBitcoindContainer.flatMap { it.containerId })
}

// Stop Bitcoind container
tasks.register<DockerStopContainer>("stopBitcoindContainer") {
    containerId.set(createBitcoindContainer.flatMap { it.containerId })
}

// Remove Bitcoind container
tasks.register<DockerRemoveContainer>("removeBitcoindContainer") {
    dependsOn("stopBitcoindContainer")
    containerId.set(createBitcoindContainer.flatMap { it.containerId })
    force.set(true)
}

// Create LND container
val createLndContainer = tasks.register<DockerCreateContainer>("createLndContainer") {
    dependsOn("pullLndImage")
    imageId.set(lndImage)
    containerName.set(lndContainerName)

    // Volume mapping
    hostConfig.binds.set(
        mapOf(
            lndDataDir to "/root/.lnd"
        )
    )


    // Port mappings
    hostConfig.portBindings.set(
        listOf(
            "9735:9735",
            "10009:10009"
        )
    )

    // Link to Bitcoind container
    hostConfig.links.set(listOf("bitcoind:bitcoind"))

    // Command line arguments
    cmd.set(
        listOf(
            "--bitcoin.active",
            "--bitcoin.regtest",
            "--bitcoin.node=bitcoind",
            "--debuglevel=info"
        )
    )
}

// Start LND container
val startLndContainer = tasks.register<DockerStartContainer>("startLndContainer") {
    dependsOn("startBitcoindContainer")
    dependsOn(createLndContainer)
    containerId.set(createLndContainer.flatMap { it.containerId })
}

// Stop LND container
tasks.register<DockerStopContainer>("stopLndContainer") {
    containerId.set(createLndContainer.flatMap { it.containerId })
}

// Remove LND container
tasks.register<DockerRemoveContainer>("removeLndContainer") {
    dependsOn("stopLndContainer")
    containerId.set(createLndContainer.flatMap { it.containerId })
    force.set(true)
}

// Start and Stop tasks for both containers
tasks.register("startContainers") {
    dependsOn("startLndContainer")
}

tasks.register("stopContainers") {
    dependsOn("removeLndContainer", "removeBitcoindContainer")
}

// Integrate Docker tasks with Spring Boot's bootRun task
tasks.named("bootRun") {
    dependsOn("startContainers")
    finalizedBy("stopContainers")
}