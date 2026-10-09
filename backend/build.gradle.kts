plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "FocusQuest backend: local-first focus session, streak, and blocking API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.13.0"

dependencies {
    // Web / API
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2")
    // Spring Boot 4 splits autoconfiguration per feature; the H2 console servlet needs its own module too.
    runtimeOnly("org.springframework.boot:spring-boot-h2console")

    // Migrations (H2 support ships inside flyway-core; no separate flyway-database-h2 module exists).
    // Spring Boot 4 splits autoconfiguration per feature, so the Flyway glue module must be added explicitly.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security / JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Spring Boot 4 moved @WebMvcTest, and the MockMvc Spring Security wiring, into their own modules.
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// The web app, built by npm and served by the backend from classpath:/static/ (see WebAppConfig), so
// `./gradlew bootRun` or the boot jar is the whole application on one origin. Tests do not include it.
// -PskipFrontendBuild uses frontend/dist as it is (the Docker image builds it in its own stage).
val frontendDir = layout.projectDirectory.dir("../frontend")
val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"
val skipFrontendBuild = providers.gradleProperty("skipFrontendBuild").isPresent

val installFrontend by tasks.registering(Exec::class) {
    description = "Installs the web app's npm dependencies."
    onlyIf { !skipFrontendBuild }
    workingDir = frontendDir.asFile
    commandLine(npm, "ci", "--silent")
    inputs.file(frontendDir.file("package-lock.json"))
    outputs.dir(frontendDir.dir("node_modules"))
}

val buildFrontend by tasks.registering(Exec::class) {
    description = "Builds the web app into frontend/dist."
    dependsOn(installFrontend)
    onlyIf { !skipFrontendBuild }
    workingDir = frontendDir.asFile
    commandLine(npm, "run", "build", "--silent")
    inputs.dir(frontendDir.dir("src"))
    inputs.dir(frontendDir.dir("public"))
    inputs.files(
        frontendDir.file("index.html"), frontendDir.file("package-lock.json"), frontendDir.file("vite.config.ts"),
        frontendDir.file("tsconfig.json"), frontendDir.file("tsconfig.app.json"), frontendDir.file("tsconfig.node.json"),
    )
    outputs.dir(frontendDir.dir("dist"))
}

// Laid out as static/ under a classpath root, for bootRun.
val webApp by tasks.registering(Sync::class) {
    dependsOn(buildFrontend)
    from(frontendDir.dir("dist"))
    into(layout.buildDirectory.dir("web-app/static"))
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    dependsOn(webApp)
    from(layout.buildDirectory.dir("web-app")) { into("BOOT-INF/classes") }
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(webApp)
    classpath(layout.buildDirectory.dir("web-app"))
}
