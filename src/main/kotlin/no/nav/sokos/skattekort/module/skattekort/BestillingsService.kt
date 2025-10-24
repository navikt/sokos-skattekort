package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
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
    val skatteetatenClient: SkatteetatenClient,
    val personService: PersonService,
) {
    fun opprettBestillingsbatch() {
        val bestillings: List<Bestilling> =
            dataSource.transaction { tx ->
                BestillingRepository
                    .getAllBestilling(tx)
                    .filter { it.bestillingsbatchId == null }
                    .take(500)
                    .toList()
            }
        if (bestillings.isEmpty()) {
            // Ingenting å gjøre
            return
        }
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

    @OptIn(ExperimentalTime::class)
    fun hentSkattekort() {
        val bestillingsbatch =
            dataSource.transaction { tx ->
                BestillingBatchRepository.getUnprocessedBatch(tx)
            } ?: return
        runBlocking {
            val response = skatteetatenClient.hentSkattekort(bestillingsbatch.bestillingsreferanse)
            if (response.status == "FORESPOERSEL_OK") {
                response.arbeidsgiver
                    .first()
                    .arbeidstaker
                    .filter { it.resultatForSkattekort == "skattekortopplysningerOK" }
                    .map { arbeidstaker ->
                        val person =
                            dataSource.transaction { tx ->
                                personService.findOrCreatePersonByFnr(
                                    tx = tx,
                                    fnr = Personidentifikator(arbeidstaker.arbeidstakeridentifikator),
                                    informasjon = "Mottatt skattekort fra Skatteetaten for bestillingsbatch: ${bestillingsbatch.id?.id}",
                                )
                            }
                        Skattekort(
                            personId = person.id!!,
                            utstedtDato = LocalDate.parse(arbeidstaker.skattekort!!.utstedtDato),
                            identifikator = arbeidstaker.skattekort.skattekortidentifikator.toString(),
                            inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                            kilde = "SKATTEETATEN",
                            forskuddstrekkList = arbeidstaker.skattekort.forskuddstrekk.map { Forskuddstrekk.create(it) },
                            tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning(it) } ?: emptyList(),
                        )
                    }
            }
        }
    }
}
