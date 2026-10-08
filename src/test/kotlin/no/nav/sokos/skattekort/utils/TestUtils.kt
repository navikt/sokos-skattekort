package no.nav.sokos.skattekort.utils

import java.io.BufferedReader
import java.io.InputStreamReader
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import java.util.stream.Collectors
import javax.sql.DataSource

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotliquery.TransactionalSession
import kotliquery.queryOf

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.withMockOAuth2Server
import no.nav.sokos.skattekort.config.ApplicationServiceOverrides
import no.nav.sokos.skattekort.config.jsonConfig
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.module
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.JWT_CLAIM_NAVIDENT
import no.nav.sokos.skattekort.security.JWT_CLAIM_ROLES
import no.nav.sokos.skattekort.security.JWT_CLAIM_SCOPES
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.security.Role
import no.nav.sokos.skattekort.security.Scope
import no.nav.sokos.skattekort.util.SQLUtils.transaction

object TestUtils {
    val eventuallyConfiguration =
        eventuallyConfig {
            initialDelay = 1.seconds
            retries = 3
        }

    fun readFile(filename: String): String {
        val inputStream = this::class.java.getResourceAsStream(filename)!!
        return BufferedReader(InputStreamReader(inputStream))
            .lines()
            .parallel()
            .collect(Collectors.joining("\n"))
    }

    var authServer: MockOAuth2Server? = null
    var oboTokenWithNavIdent: String? = null
    var m2mTokenWithNavIdent: String? = null

    fun withFullTestApplication(thunk: suspend ApplicationTestBuilder.() -> Unit) =
        withMockOAuth2Server {
            authServer = this
            oboTokenWithNavIdent =
                this
                    .issueToken(
                        issuerId = "default",
                        claims =
                            mapOf(
                                JWT_CLAIM_NAVIDENT to "aUser",
                                JWT_CLAIM_SCOPES to Scope.entries.joinToString(" ") { it.value },
                            ),
                    ).serialize()
            m2mTokenWithNavIdent =
                this
                    .issueToken(
                        issuerId = "default",
                        claims =
                            mapOf(
                                JWT_CLAIM_ROLES to Role.entries.map { it.value }.toTypedArray(),
                            ),
                    ).serialize()

            testApplication {
                application {
                    configureTestModule(authServer!!)
                }
                startApplication()

                client =
                    createClient {
                        install(ContentNegotiation) {
                            json(
                                jsonConfig,
                            )
                        }
                    }
                thunk()
            }
        }

    fun Application.configureTestModule(authServer: MockOAuth2Server) {
        module(
            testEnvironmentConfig(authServer),
            ApplicationServiceOverrides(
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                tilgangsmaskinUrl = WiremockListener.wiremock.baseUrl(),
                skatteetatenUrl = WiremockListener.wiremock.baseUrl(),
                darePocUrl = WiremockListener.wiremock.baseUrl(),
                pdlTokenClient = mockk<AzuredTokenClient>(relaxed = true),
                tilgangsmaskinTokenClient = mockk<AzuredTokenClient>(relaxed = true),
                darePocTokenClient = mockk<AzuredTokenClient>(relaxed = true),
                maskinportenTokenClient = mockk<MaskinportenTokenClient>(relaxed = true),
                connectionFactory = MQListener.connectionFactory,
                forespoerselQueue = MQListener.forespoerselQueue,
                forespoerselBoqQueue = MQListener.forespoerselBoqQueue,
                utsendingQueue = MQListener.utsendingsQueue,
                utsendingStorQueue = MQListener.utsendingStorQueue,
            ),
        )
    }

    fun runThisSql(query: String) {
        DbListener.dataSource.transaction { session ->
            session.run(
                queryOf(
                    query,
                ).asExecute,
            )
        }
        updateIdentitySequences(DbListener.dataSource)
    }

    fun updateIdentitySequences(dataSource: DataSource) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = TRANSACTION_SERIALIZABLE

            val metadata = connection.metaData

            val tables =
                metadata.getTables(null, null, null, arrayOf("TABLE")).use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val schema = resultSet.getString("TABLE_SCHEM")
                            val tableName = resultSet.getString("TABLE_NAME")
                            if (tableName.uppercase() != "FLYWAY_SCHEMA_HISTORY" && tableName.uppercase() != "SCHEDULED_TASKS_HISTORY") {
                                add(schema to tableName)
                            }
                        }
                    }
                }

            val tablesWithId =
                tables.mapNotNull { (schema, table) ->
                    metadata.getColumns(null, schema, table, "id").use { rs ->
                        if (rs.next()) "$schema.$table" else null
                    }
                }

            tablesWithId.asReversed().forEach { table ->
                connection
                    .prepareStatement(
                        "SELECT setval(pg_get_serial_sequence('$table', 'id'), " +
                            "COALESCE((SELECT MAX(id) FROM $table), 0) + 1, false);",
                    ).use { it.execute() }
            }

            connection.commit()
        }
    }

    fun <T> tx(block: (TransactionalSession) -> T): T = DbListener.dataSource.transaction { tx -> block(tx) }

    private fun testEnvironmentConfig(authServer: MockOAuth2Server): MapApplicationConfig =
        MapApplicationConfig().apply {
            put("APPLICATION_ENV", "TEST")

            // Database properties
            put("DB_PORT", DbListener.container.firstMappedPort.toString())
            put("DB_HOST", DbListener.container.host)
            put("AZURE_APP_WELL_KNOWN_URL", authServer.wellKnownUrl("default").toUrl().toString())
        }
}
