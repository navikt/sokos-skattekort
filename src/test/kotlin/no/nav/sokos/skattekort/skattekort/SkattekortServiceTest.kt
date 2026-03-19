package no.nav.sokos.skattekort.skattekort

import java.math.BigDecimal
import java.time.LocalDate

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk

import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.skattekort.Forskuddstrekk.Companion.ForskuddstrekkType
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.utils.TestUtils.tx

@OptIn(ExperimentalAtomicApi::class)
class SkattekortServiceTest :
    FunSpec({
        extensions(DbListener)

        val mockAuditLogger: AuditLogger = mockk<AuditLogger>()
        val tilgangsmaskinClientService = mockk<TilgangsmaskinClientService>(relaxed = true)

        val skattekortService: SkattekortService by lazy {
            SkattekortService(
                dataSource = DbListener.dataSource,
                personService =
                    PersonService(
                        dataSource = DbListener.dataSource,
                        pdlClientService = mockk(),
                    ),
                auditLogger = mockAuditLogger,
                tilgangsmaskinClientService = tilgangsmaskinClientService,
            )
        }

        test("hentSingleSkattekortForEachYear should return the latest skattekort for each year") {
            withConstantNow(LocalDate.parse("2021-12-24").atStartOfDay()) {
                databaseHas(
                    aPerson(1L),
                    afoedselsnummer(1L, "01410100001"),
                    aSkattekort(id = 1L, personId = 1L, inntektsaar = 2020, resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort),
                    aSkattekort(id = 2L, personId = 1L, inntektsaar = 2020, resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort),
                    aSkattekort(id = 3L, personId = 1L, inntektsaar = 2020, resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK),
                    aDbForskuddstrekk(1L, 3L, ForskuddstrekkType.TABELLKORT.type, Trekkode.LOENN_FRA_NAV, tabellNummer = "8765", prosentSats = 13.37, antMndForTrekk = 4.00),
                    aSkattekort(id = 4L, personId = 1L, inntektsaar = 2021, resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort),
                    aSkattekort(id = 5L, personId = 1L, inntektsaar = 2021, resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort),
                    aSkattekort(id = 6L, personId = 1L, inntektsaar = 2021, resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK),
                    aDbForskuddstrekk(2L, 6L, ForskuddstrekkType.PROSENTKORT.type, Trekkode.UFOERETRYGD_FRA_NAV, prosentSats = 81.28),
                    aSkattekort(id = 7L, personId = 1L, inntektsaar = 2022, resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort),
                    aSkattekort(id = 8L, personId = 1L, inntektsaar = 2022, resultatForSkattekort = ResultatForSkattekort.IkkeSkattekort),
                    aSkattekort(id = 9L, personId = 1L, inntektsaar = 2022, resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK),
                    aDbForskuddstrekk(3L, 9L, ForskuddstrekkType.FRIKORT.type, Trekkode.PENSJON_FRA_NAV, frikortbeløp = null),
                )

                val skattekort = skattekortService.getSkattekort("01410100001").get()
                skattekort shouldNotBeNull {
                    size shouldBe 9
                }
                val onlyLastSkattekort = skattekortService.getSingleSkattekortForEachYear("01410100001").get()
                onlyLastSkattekort.shouldBeFunctionallyEquivalentTo(
                    listOf(
                        aDomainSkattekort(
                            inntektsaar = 2022,
                            resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                            forskuddstrekk = Frikort(trekkode = Trekkode.PENSJON_FRA_NAV, frikortBeloep = null),
                            personId = 1L,
                        ),
                        aDomainSkattekort(
                            inntektsaar = 2021,
                            resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                            forskuddstrekk = Prosentkort(trekkode = Trekkode.UFOERETRYGD_FRA_NAV, prosentSats = BigDecimal.valueOf(81.28)),
                            personId = 1L,
                        ),
                        aDomainSkattekort(
                            inntektsaar = 2020,
                            resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                            forskuddstrekk =
                                Tabellkort(
                                    trekkode = Trekkode.LOENN_FRA_NAV,
                                    tabellNummer = "8765",
                                    prosentSats = BigDecimal.valueOf(13.37),
                                    antallMndForTrekk = BigDecimal.valueOf(4.0),
                                ),
                            personId = 1L,
                        ),
                    ),
                )
            }
        }

        test("deleteSkattekortForYear should delete all rows for target year in chunks and keep other years untouched") {
            val yearToDelete = 2025
            val yearToKeep = 2026

            val keepIds = listOf(50001L, 50002L, 50003L)
            val skattekortId = AtomicLong(0L)
            val rowsForDeleteYear =
                (1L..10005L).flatMap {
                    var id = skattekortId.incrementAndFetch()
                    listOf(
                        aSkattekort(
                            id = id,
                            personId = 1L,
                            inntektsaar = yearToDelete,
                            identifikator = "delete-$id",
                        ),
                        aSkattekort(
                            id = skattekortId.incrementAndFetch(),
                            personId = 1L,
                            inntektsaar = yearToDelete,
                            identifikator = "delete-$id",
                            generertFra = id,
                        ),
                        aForskuddstrekk(
                            skattekortId = id,
                            type =
                                Prosentkort(
                                    trekkode = Trekkode.LOENN_FRA_NAV,
                                    prosentSats = BigDecimal.valueOf(25.0),
                                ),
                            trekkode = Trekkode.LOENN_FRA_NAV,
                            prosentSats = 25.0,
                        ),
                        aTilleggsopplysning(
                            skattekortId = id,
                            opplysning = Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE,
                        ),
                        aSkattekortData(
                            dataMottatt = """{"identifikator":"delete-$id"}""",
                            inntektsaar = yearToDelete,
                            fnr = "01410100001",
                            skattekortId = id,
                        ),
                    )
                }

            databaseHas(
                aPerson(1L),
                afoedselsnummer(1L, "01410100001"),
                rowsForDeleteYear.joinToString("\n"),
                aSkattekort(
                    id = keepIds[0],
                    personId = 1L,
                    inntektsaar = yearToKeep,
                    identifikator = "keep-${keepIds[0]}",
                ),
                aSkattekort(
                    id = keepIds[1],
                    personId = 1L,
                    inntektsaar = yearToKeep,
                    identifikator = "keep-${keepIds[1]}",
                ),
                aSkattekort(
                    id = keepIds[2],
                    personId = 1L,
                    inntektsaar = yearToKeep,
                    identifikator = "keep-${keepIds[2]}",
                ),
            )

            tx { SkattekortRepository.getAllIdByInntektsaar(it, yearToDelete) }.size shouldBe 20010
            tx { SkattekortRepository.getAllIdByInntektsaar(it, yearToKeep) } shouldBe keepIds

            skattekortService.deleteSkattekortForYear(yearToDelete)

            tx { SkattekortRepository.getAllIdByInntektsaar(it, yearToDelete) } shouldBe emptyList()
            tx { SkattekortRepository.getAllIdByInntektsaar(it, yearToKeep) } shouldBe keepIds
        }
    })
