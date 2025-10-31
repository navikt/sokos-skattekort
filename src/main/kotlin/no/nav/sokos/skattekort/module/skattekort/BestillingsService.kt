package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.module.forespoersel.AbonnementRepository
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.Arbeidsgiver
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.ArbeidsgiverIdentifikator
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortRequest
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.ForespoerselOmSkattekortTilArbeidsgiver
import no.nav.sokos.skattekort.skatteetaten.bestillskattekort.Kontaktinformasjon
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
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
            BestillSkattekortRequest(
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
        dataSource.transaction { tx ->
            BestillingBatchRepository.getUnprocessedBatch(tx)?.let { bestillingsbatch ->
                runBlocking {
                    val response = skatteetatenClient.hentSkattekort(bestillingsbatch.bestillingsreferanse)
                    if (response.status == "FORESPOERSEL_OK") {
                        response.arbeidsgiver
                            .first()
                            .arbeidstaker
                            .map { arbeidstaker ->
                                val person = getPerson(arbeidstaker, bestillingsbatch)
                                val inntektsaar = arbeidstaker.inntektsaar.toInt()
                                SkattekortRepository.insertBatch(
                                    tx,
                                    listOf(
                                        toSkattekort(arbeidstaker, person, bestillingsbatch),
                                    ),
                                )
                                AbonnementRepository.finnAktiveAbonnement(tx, person.id!!).forEach { (aboid, system) ->
                                    UtsendingRepository.insert(
                                        tx,
                                        Utsending(
                                            abonnementId = aboid,
                                            inntektsaar = inntektsaar,
                                            fnr = person.foedselsnummer.fnr,
                                            forsystem = system,
                                        ),
                                    )
                                }
                            }
                        BestillingBatchRepository.markAsProcessed(tx, bestillingsbatch.id!!.id)
                        BestillingRepository.deleteProcessedBestillings(tx, bestillingsbatch.id.id)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun toSkattekort(
        arbeidstaker: Arbeidstaker,
        person: Person,
        bestillingsbatch: BestillingBatch,
    ): Skattekort =
        when (ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort)) {
            ResultatForSkattekort.SkattekortopplysningerOK ->
                Skattekort(
                    personId = person.id!!,
                    utstedtDato = LocalDate.parse(arbeidstaker.skattekort!!.utstedtDato),
                    identifikator = arbeidstaker.skattekort.skattekortidentifikator.toString(),
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = "SKATTEETATEN",
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    forskuddstrekkList = arbeidstaker.skattekort.forskuddstrekk.map { Forskuddstrekk.create(it) },
                    tilleggsopplysningList = arbeidstaker.tilleggsopplysning?.map { Tilleggsopplysning(it) } ?: emptyList(),
                )

            else ->
                Skattekort(
                    personId = person.id!!,
                    utstedtDato = bestillingsbatch.oppdatert.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    identifikator = "",
                    inntektsaar = Integer.parseInt(arbeidstaker.inntektsaar),
                    kilde = "SKATTEETATEN",
                    resultatForSkattekort = ResultatForSkattekort.fromValue(arbeidstaker.resultatForSkattekort),
                )
        }

    private fun getPerson(
        arbeidstaker: Arbeidstaker,
        bestillingsbatch: BestillingBatch,
    ): Person =
        dataSource.transaction { tx ->
            personService.findOrCreatePersonByFnr(
                tx = tx,
                fnr = Personidentifikator(arbeidstaker.arbeidstakeridentifikator),
                informasjon = "Mottatt skattekort fra Skatteetaten for bestillingsbatch: ${bestillingsbatch.id?.id}",
            )
        }
}
