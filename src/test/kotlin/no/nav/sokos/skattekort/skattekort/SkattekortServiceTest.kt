package no.nav.sokos.skattekort.skattekort

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldContainAllIgnoringFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.skattekort.Forskuddstrekk.Companion.ForskuddstrekkType
import no.nav.sokos.skattekort.util.audit.AuditLogger

class SkattekortServiceTest :
    FunSpec({
        extensions(DbListener)

        val mockAuditLogger: AuditLogger = mockk<AuditLogger>()

        val skattekortService: SkattekortService by lazy {
            SkattekortService(
                dataSource = DbListener.dataSource,
                personService =
                    PersonService(
                        dataSource = DbListener.dataSource,
                        pdlClientService = mockk(),
                    ),
                auditLogger = mockAuditLogger,
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

                val skattekort = skattekortService.getSkattekort("01410100001")
                println("skattekort = $skattekort")
                skattekort shouldNotBeNull {
                    size shouldBe 9
                }
                val onlyLastSkattekort = skattekortService.getSingleSkattekortForEachYear("01410100001")
                onlyLastSkattekort shouldBeFunctionallyEquivalentTo
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
                    )
            }
        }
    })

fun aSkattekort(
    id: Long,
    personId: Long,
    inntektsaar: Int = LocalDate.now().year,
    opprettet: LocalDateTime = LocalDateTime.now(),
    identifikator: String = "1",
    utstedtDato: LocalDate = LocalDate.now(),
    resultatForSkattekort: ResultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
) = aDbSkattekort(
    id = id,
    personId = personId,
    utstedtDato = utstedtDato.toString(),
    identifikator = identifikator,
    inntektsaar = inntektsaar,
    opprettet = opprettet.toString(),
    resultatForSkattekort = resultatForSkattekort,
)

fun aDomainSkattekort(
    inntektsaar: Int,
    resultatForSkattekort: ResultatForSkattekort,
    forskuddstrekk: Forskuddstrekk,
    personId: Long,
) = Skattekort(
    inntektsaar = inntektsaar,
    resultatForSkattekort = resultatForSkattekort,
    forskuddstrekkList = listOf(forskuddstrekk),
    personId = PersonId(personId),
    identifikator = "01410100001",
    utstedtDato = kotlinx.datetime.LocalDate.parse("2021-01-01"),
    kilde = "foo",
)

infix fun List<Skattekort>.shouldBeFunctionallyEquivalentTo(expected: List<Skattekort>) {
    this.shouldContainAllIgnoringFields(
        expected,
        Skattekort::id,
        Skattekort::generertFra,
        Skattekort::utstedtDato,
        Skattekort::identifikator,
        Skattekort::kilde,
        Skattekort::opprettet,
        Skattekort::tilleggsopplysningList,
    )
//    this.size shouldBe expected.size
}
