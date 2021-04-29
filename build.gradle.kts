val junitJupiterVersion = "5.7.0"

plugins {
    kotlin("jvm") version "1.4.30"
}

repositories {
    mavenCentral()
    maven("https://kotlin.bintray.com/ktor")
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.navikt:rapids-and-rivers:1.5e3ca6a")

    implementation("org.flywaydb:flyway-core:6.5.0")
    implementation("com.zaxxer:HikariCP:3.4.5")
    implementation("no.nav:vault-jdbc:1.3.7")
    implementation("com.github.seratch:kotliquery:1.3.1")

    implementation("com.bazaarvoice.jackson:rison:2.9.10.2")

    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")

    testImplementation("io.mockk:mockk:1.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "14"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "14"
    }

    named<Jar>("jar") {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "no.nav.helse.spammer.AppKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("$buildDir/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<Wrapper> {
        gradleVersion = "6.8.3"
    }
}
