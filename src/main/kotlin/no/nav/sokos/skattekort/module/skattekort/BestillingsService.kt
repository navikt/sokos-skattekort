package no.nav.sokos.skattekort.domain.bestilling

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.domain.skattekort.BestillingBatchRepository
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
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
    val dataSource: HikariDataSource,
    val bestillingRepository: BestillingRepository,
    val bestillingsbatchRepository: BestillingBatchRepository,
    val skatteetatenClient: SkatteetatenClient,
) {
    suspend fun opprettBestillingsbatch() {
        val (bestillings, request) =
            dataSource.transaction { tx ->
                val bestillings =
                    bestillingRepository
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

        val response = skatteetatenClient.bestillSkattekort(request)

        dataSource.transaction { tx ->
            val bestillingsbatchId =
                bestillingsbatchRepository.insert(
                    tx,
                    bestillingsreferanse = response.bestillingsreferanse,
                    request = request,
                )
            bestillingRepository.updateBestillingsWithBatchId(
                tx,
                bestillings.map { it.id!!.id },
                bestillingsbatchId,
            )
        }
    }
}
