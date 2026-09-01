import kotlinx.kover.gradle.plugin.dsl.tasks.KoverReport

import com.expediagroup.graphql.plugin.gradle.config.GraphQLSerializer
import com.expediagroup.graphql.plugin.gradle.tasks.GraphQLGenerateClientTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("io.github.androa.gradle.plugin.avro") version "0.0.12"
    id("com.expediagroup.graphql") version "10.2.2"
    id("org.openapi.generator") version "7.25.0"

    application
}

group = "no.nav.sokos"

repositories {
    mavenCentral()

    maven { url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven { url = uri("https://packages.confluent.io/maven/") }
}

val resilience4jVersion = "2.4.0"
val ktorVersion = "3.5.2"
val nimbusVersion = "10.9.1"
val logbackVersion = "1.6.3"
val logstashVersion = "9.0"
val micrometerVersion = "1.17.1"
val dbSchedulerVersion = "16.12.0"
val kotlinLoggingVersion = "3.0.5"
val kotestVersion = "6.2.4"
val kotlinxSerializationVersion = "1.11.0"
val kotlinxDatetimeVersion = "0.8.0-0.6.x-compat"
val mockOAuth2ServerVersion = "6.0.2"
val mockkVersion = "1.14.11"
val hikariVersion = "7.1.0"
val kotliqueryVersion = "2.1.0"
val testcontainersVersion = "1.21.4"
val flywayVersion = "13.3.0"
val postgresVersion = "42.7.13"
val activemqVersion = "2.55.0"
val ibmmqVersion = "10.0.0.0"
val opentelemetryVersion = "2.31.1-alpha"
val swaggerRequestValidatorVersion = "3.0.0"
val kafkaClientsVersion = "4.3.1"
val avroVersion = "1.12.2"
val kafkaAvroSerializerVersion = "8.1.1"
val avro4kVersion = "2.6.0"
val graphqlClientVersion = "10.2.2"
val wiremockVersion = "3.13.2"
val unleashedVersion = "12.2.3"

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
    implementation("no.nav:kotliquery:$kotliqueryVersion")

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
                useTarget("at.yawk.lz4:lz4-java:1.11.2")
                because("Prefer the patched fork for vulnerability fix")
            }
            if (requested.group == "io.netty") {
                useVersion("4.2.16.Final")
                because("Multiple versions of netty has vulnerable dependencies. Affected version < 4.2.15.Final")
            }
            if (requested.group == "com.fasterxml.jackson.core" && requested.name == "jackson-core") {
                useVersion("2.22.1")
                because("jackson-core: Number Length Constraint Bypass in Async Parser Leads to Potential DoS Condition. Affected version >= 2.19.0, < 2.21.1")
            }
            if (requested.group == "tools.jackson.core" && requested.name == "jackson-core") {
                useVersion("3.2.1")
                because("Jackson Core: Document length constraint bypass in blocking, async, and DataInput parsers. Affected version >= 3.0.0, <= 3.1.0")
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
        dependsOn("openApiGenerate")
    }

    named("runKtlintFormatOverMainSourceSet").configure {
        dependsOn("graphqlGenerateClient")
        dependsOn("openApiGenerate")
    }

    withType<KotlinCompile>().configureEach {
        dependsOn("ktlintFormat")
        dependsOn("graphqlGenerateClient")
        dependsOn("openApiGenerate")
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

    withType<GenerateTask>().configureEach {
        generatorName.set("kotlin")
        inputSpec.set("$rootDir/src/main/resources/tilgangsmaskinen/openapi.json")
        outputDir.set("${layout.buildDirectory.get()}/generated")
        modelPackage.set("no.nav.tilgangsmaskinen")
        generateApiTests.set(false)
        generateModelTests.set(false)
        configOptions.set(
            mapOf(
                "library" to "jvm-ktor",
                "serializationLibrary" to "kotlinx_serialization",
            ),
        )
        globalProperties.set(
            mapOf(
                "models" to "",
            ),
        )
        typeMappings.set(
            mapOf(
                "URI" to "kotlin.String",
            ),
        )
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
