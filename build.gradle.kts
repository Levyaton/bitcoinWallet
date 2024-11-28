import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer.ExposedPort
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer.HostConfig
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import org.gradle.api.tasks.Exec


plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.3.4"
	id("io.spring.dependency-management") version "1.1.6"
	kotlin("plugin.jpa") version "1.9.25"
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
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.microsoft.sqlserver:mssql-jdbc")
	implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.0")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
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

val sqlServerImage = "mcr.microsoft.com/mssql/server:2019-latest"
val sqlServerContainerName = "sqlserver"

val saPassword = System.getenv("SA_PASSWORD") ?: "YourStrong!Passw0rd"

tasks.register<DockerPullImage>("pullSqlServerImage") {
	// Pull the SQL Server Docker image
	image.set(sqlServerImage)
}

tasks.register<com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer>("createSqlServerContainer") {
	dependsOn("pullSqlServerImage")
	targetImageId(sqlServerImage)
	containerName.set(sqlServerContainerName)

	// Set environment variables
	envVars.set(
		mapOf(
			"ACCEPT_EULA" to "Y",
			"SA_PASSWORD" to saPassword // Use a strong password, preferably from environment variables
		)
	)

	// Expose container port 1433 (SQL Server default port)
	exposedPorts.set(
		listOf(
			ExposedPort("tcp", listOf(1433))
		)
	)

	// Configure HostConfig
	hostConfig.portBindings.addAll(listOf(
		"1433:1433" // Maps host port 1433 to container port 1433
	))
}

tasks.register<DockerStartContainer>("startSqlServerContainer") {
	dependsOn("createSqlServerContainer")
	targetContainerId(sqlServerContainerName)
}

tasks.register<DockerStopContainer>("stopSqlServerContainer") {
	targetContainerId(sqlServerContainerName)
}

tasks.register<DockerRemoveContainer>("removeSqlServerContainer") {
	dependsOn("stopSqlServerContainer")
	targetContainerId(sqlServerContainerName)
	force.set(true)
}

tasks.register("startSqlServer") {
	dependsOn("startSqlServerContainer")
}

tasks.register("stopSqlServer") {
	dependsOn("removeSqlServerContainer")
}

// Integrate Docker tasks with Spring Boot's bootRun task
tasks.named("bootRun") {
	dependsOn("startSqlServer")
	finalizedBy("stopSqlServer")
}