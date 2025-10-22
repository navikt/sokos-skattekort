package no.nav.sokos.skattekort.module.skattekort

import kotlinx.coroutines.runBlocking

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.skatteetaten.Arbeidsgiver
import no.nav.sokos.skattekort.skatteetaten.ArbeidsgiverIdentifikator
import no.nav.sokos.skattekort.skatteetaten.ForespoerselOmSkattekortTilArbeidsgiver
import no.nav.sokos.skattekort.skatteetaten.Kontaktinformasjon
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenBestillSkattekortRequest
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.util.SQLUtils.transaction

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class BestillingsService(
    val dataSource: HikariDataSource = DatabaseConfig.dataSource,
    val skatteetatenClient: SkatteetatenClient = SkatteetatenClient(),
) {
    fun opprettBestillingsbatch() {
        val (bestillings, request) =
            dataSource.transaction { tx ->
                val bestillings =
                    BestillingRepository
                        .getAllBestilling(tx)
                        .filter { it.bestillingsbatchId == null }
                        .take(500)
                        .toList()
                val request =
                    SkatteetatenBestillSkattekortRequest(
                        inntektsaar = "2025",
                        bestillingstype = "HENT_ALLE_OPPGITTE",
                        kontaktinformasjon =
                            Kontaktinformasjon(
                                epostadresse = "john.smith@example.com",
                                mobiltelefonummer = "+4794123456",
                            ),
                        varslingstype = "VARSEL_VED_FOERSTE_ENDRING",
                        forespoerselOmSkattekortTilArbeidsgiver =
                            ForespoerselOmSkattekortTilArbeidsgiver(
                                arbeidsgiver =
                                    listOf(
                                        Arbeidsgiver(
                                            arbeidsgiveridentifikator = ArbeidsgiverIdentifikator("312978083"),
                                            arbeidstakeridentifikator = bestillings.map { it.fnr }.map { it.value },
                                        ),
                                    ),
                            ),
                    )
                bestillings to request
            }

        if (bestillings.isEmpty()) {
            // Ingenting å gjøre
            return
        }
        runBlocking {
            val response = skatteetatenClient.bestillSkattekort(request)

            dataSource.transaction { tx ->
                val bestillingsbatchId =
                    BestillingBatchRepository.insert(
                        tx,
                        bestillingsreferanse = response.bestillingsreferanse,
                        request = request,
                    )
                BestillingRepository.updateBestillingsWithBatchId(
                    tx,
                    bestillings.map { it.id!!.id },
                    bestillingsbatchId,
                )
            }
        }
    }
}
