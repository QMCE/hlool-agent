plugins {
    kotlin("multiplatform") version "2.3.20"
}

group = "rj.cocacode"
version = "0.4.0"

kotlin {
    jvm {
        binaries {
            executable {
                mainClass = "rj.cocacode.MainKt"
            }
        }
    }
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
                implementation("io.ktor:ktor-client-core:2.3.13")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
                implementation("io.ktor:ktor-client-logging:2.3.13")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
                implementation("com.github.ajalt.clikt:clikt:4.2.1")
                implementation("org.slf4j:slf4j-api:2.0.9")
                implementation("ch.qos.logback:logback-classic:1.5.32")
                implementation("io.ktor:ktor-client-cio:2.3.13")
                implementation("org.jline:jline:3.27.1")
                implementation("org.yaml:snakeyaml:2.2")
                implementation("com.google.code.gson:gson:2.10.1")
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            }
        }
        val mingwX64Main by getting {
            dependencies {
                implementation("io.ktor:ktor-client-winhttp:2.3.13")
            }
        }
        val mingwX64Test by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.named<Jar>("jvmJar") {
    manifest {
        attributes["Main-Class"] = "rj.cocacode.MainKt"
    }
}

tasks.named<Test>("jvmTest") {
    useJUnit()
}
