plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.vasilevyuv"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.tomcat.jakarta.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.tomcat)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.auth.asProvider())
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.websockets)
    implementation(libs.logback.classic)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.bcrypt)
    runtimeOnly(libs.swagger.ui)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.websockets)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.swagger.parser)
}

tasks.processResources {
    val swaggerUiVersion = libs.versions.swagger.ui.get()
    inputs.property("swaggerUiVersion", swaggerUiVersion)
    filesMatching("documentation/index.html") {
        expand("swaggerUiVersion" to swaggerUiVersion)
    }
}
