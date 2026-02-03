import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
    id("io.github.androa.gradle.plugin.avro") version "0.0.12"
    id("com.expediagroup.graphql") version "8.8.1"

    application
}

group = "no.nav.sokos"

repositories {
    mavenCentral()

    maven { url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven { url = uri("https://packages.confluent.io/maven/") }
}

val resilience4jVersion = "2.3.0"
val ktorVersion = "3.4.0"
val nimbusVersion = "10.7"
val logbackVersion = "1.5.26"
val logstashVersion = "9.0"
val micrometerVersion = "1.16.2"
val dbSchedulerVersion = "16.7.0"
val kotlinLoggingVersion = "3.0.5"
val janionVersion = "3.1.12"
val kotestVersion = "6.1.1"
val kotlinxSerializationVersion = "1.10.0"
val kotlinxDatetimeVersion = "0.7.1-0.6.x-compat"
val mockOAuth2ServerVersion = "3.0.1"
val mockkVersion = "1.14.9"
val hikariVersion = "7.0.2"
val kotliqueryVersion = "1.9.1"
val testcontainersVersion = "1.21.4"
val flywayVersion = "11.20.3"
val postgresVersion = "42.7.9"
val activemqVersion = "2.50.0"
val ibmmqVersion = "9.4.4.1"
val opentelemetryVersion = "2.24.0-alpha"
val swaggerRequestValidatorVersion = "2.46.0"
val kafkaClientsVersion = "8.1.1-ce"
val avroVersion = "1.12.1"
val kafkaAvroSerializerVersion = "8.1.1"
val avro4kVersion = "2.6.0"
val graphqlClientVersion = "8.8.1"
val wiremockVersion = "3.13.2"
val unleashedVersion = "12.1.0"

dependencies {

    // Ktor server
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation("io.ktor:ktor-server-di:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")

    // Ktor client
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-apache5-jvm:$ktorVersion")

    // Security
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusVersion")

    // Database
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:$kotlinxDatetimeVersion")

    // Monitorering
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-kotlin:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-micrometer:$resilience4jVersion")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    runtimeOnly("org.codehaus.janino:janino:$janionVersion")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:$logstashVersion")

    implementation("jakarta.jms:jakarta.jms-api:3.1.0")
    implementation("com.ibm.mq:com.ibm.mq.jakarta.client:$ibmmqVersion")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("io.confluent:kafka-avro-serializer:$kafkaAvroSerializerVersion")

    // Scheduler
    implementation("com.github.kagkarlsson:db-scheduler:$dbSchedulerVersion")

    // GraphQL
    implementation("com.expediagroup:graphql-kotlin-ktor-client:$graphqlClientVersion") {
        exclude("com.expediagroup:graphql-kotlin-client-jackson")
    }

    // Opentelemetry
    implementation("io.opentelemetry.instrumentation:opentelemetry-ktor-3.0:$opentelemetryVersion")

    // Feature switches
    implementation("io.getunleash:unleash-client-java:$unleashedVersion")

    // Test
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-extensions-now:$kotestVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("io.kotest.extensions:kotest-extensions-testcontainers:2.0.2")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.apache.activemq:artemis-jakarta-server:$activemqVersion")
    testImplementation("com.atlassian.oai:swagger-request-validator-restassured:$swaggerRequestValidatorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.wiremock:wiremock:$wiremockVersion")
}

// Transitive dependencvy lz4-java is fixed only in fork at.yawk, therefore this reference
// Remove when fixed in next release of org.apache.kafka:kafka-clients
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.lz4" && requested.name == "lz4-java") {
                useTarget("at.yawk.lz4:lz4-java:1.10.3")
                because("Prefer the patched fork for vulnerability fix")
            }
        }
    }
}

application {
    mainClass.set("no.nav.sokos.skattekort.ApplicationKt")
}

sourceSets {
    main {
        java {
            srcDirs("${layout.buildDirectory.get()}/generated/src/main/kotlin")
        }
    }
}

generateAvro {
    schemas.from(layout.projectDirectory.dir("src/main/avro/"))
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    named("runKtlintCheckOverMainSourceSet").configure {
        dependsOn("graphqlGenerateClient")
    }

    named("runKtlintFormatOverMainSourceSet").configure {
        dependsOn("graphqlGenerateClient")
    }

    withType<KotlinCompile>().configureEach {
        dependsOn("ktlintFormat")
        dependsOn("graphqlGenerateClient")
    }

    withType<KoverReport>().configureEach {
        kover {
            reports {
                filters {
                    excludes {
                        // exclusion rules - classes to exclude from report
                        classes("no.nav.pdl.*")
                    }
                }
            }
        }
    }

    withType<GraphQLGenerateClientTask>().configureEach {
        packageName.set("no.nav.pdl")
        schemaFile.set(file("$projectDir/src/main/resources/graphql/schema.graphql"))
        queryFileDirectory.set(file("$projectDir/src/main/resources/graphql"))
        outputDirectory.set(file("$projectDir/build/generated/sources/graphql/main"))
        serializer = GraphQLSerializer.KOTLINX
    }

    withType<Test>().configureEach {
        useJUnitPlatform()

        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = FULL
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }

        reports.forEach { report -> report.required.value(false) }

        finalizedBy(koverHtmlReport)
    }

    withType<Wrapper> {
        gradleVersion = "9.2.1"
    }

    ("build") {
        dependsOn("copyPreCommitHook")
    }

    register<Copy>("copyPreCommitHook") {
        from(".scripts/pre-commit")
        into(".git/hooks")
        filePermissions {
            user {
                execute = true
            }
        }
        doFirst {
            println("Installing git hooks...")
        }
        doLast {
            println("Git hooks installed successfully.")
        }
        description = "Copy pre-commit hook to .git/hooks"
        group = "git hooks"
        outputs.upToDateWhen { false }
    }
}
