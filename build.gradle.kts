plugins {
    kotlin("jvm") version "2.3.20"
    application
}

group = "rj.cocacode"
version = "0.1.1"

dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    
    // CLI
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    
    // HTTP Client
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-cio:2.3.13")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
    implementation("io.ktor:ktor-client-logging:2.3.13")
    
    // Terminal
    implementation("org.jline:jline:3.27.1")
    
    // YAML
    implementation("org.yaml:snakeyaml:2.2")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass = "rj.cocacode.MainKt"
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "rj.cocacode.MainKt"
    }
}

tasks.named<Test>("test") {
    useJUnit()
}

