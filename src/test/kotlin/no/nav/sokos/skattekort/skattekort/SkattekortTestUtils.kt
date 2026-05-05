package no.nav.sokos.skattekort.skattekort

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

import io.kotest.matchers.collections.shouldContainAllIgnoringFields

import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Skattekort as Sskattekort
import no.nav.sokos.skattekort.forespoersel.Forsystem
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidsgiver
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidsgiveridentifikator
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchId
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType
import no.nav.sokos.skattekort.utils.TestUtils.runThisSql

fun aSkattekort(
    id: Long,
    personId: Long,
    inntektsaar: Int,
    utstedtDato: LocalDate = LocalDate.now(),
    identifikator: String = "1",
    opprettet: LocalDateTime = LocalDateTime.now(),
    kilde: String = "skatteetaten",
    resultatForSkattekort: ResultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
    generertFra: Long? = null,
) = """
    INSERT INTO skattekort (id, person_id, utstedt_dato, identifikator, inntektsaar, kilde, opprettet, resultatForSkattekort, generert_fra)
    VALUES ($id, $personId, '$utstedtDato', '$identifikator', $inntektsaar, '$kilde', '$opprettet', '${resultatForSkattekort.value}', $generertFra);
    """.trimIndent()

fun aForskuddstrekk(
    id: Long? = null,
    skattekortId: Long,
    type: Forskuddstrekk,
    trekkode: Trekkode,
    prosentSats: Double? = null,
    antMndForTrekk: Double? = null,
    tabellNummer: String? = null,
    frikortbeløp: Int? = null,
): String {
    val idColumn = if (id != null) "id, " else ""
    val idValue = if (id != null) "$id, " else ""

    return when (type) {
        is Prosentkort ->
            """
            INSERT INTO forskuddstrekk (${idColumn}skattekort_id, type, trekk_kode, prosentsats, antall_mnd_for_trekk, tabell_nummer, frikort_beloep)
            VALUES (${idValue}$skattekortId, 'PROSENTKORT', '${trekkode.value}', ${prosentSats!!}, ${antMndForTrekk?.toString() ?: "NULL"}, NULL, NULL);
            """.trimIndent()

        is Tabellkort ->
            """
            INSERT INTO forskuddstrekk ($idColumn skattekort_id, type, trekk_kode, prosentsats, antall_mnd_for_trekk, tabell_nummer, frikort_beloep)
            VALUES ($idValue $skattekortId, 'TABELLKORT', '${trekkode.value}', ${prosentSats!!}, ${antMndForTrekk ?: 12.0}, '${tabellNummer!!}', NULL);
            """.trimIndent()

        is Frikort ->
            """
            INSERT INTO forskuddstrekk ($idColumn skattekort_id, type, trekk_kode, prosentsats, antall_mnd_for_trekk, tabell_nummer, frikort_beloep)
            VALUES (${idValue}$skattekortId, 'FRIKORT', '${trekkode.value}', NULL, NULL, NULL, ${frikortbeløp ?: "NULL"});
            """.trimIndent()
    }
}

fun aTilleggsopplysning(
    id: Long? = null,
    skattekortId: Long,
    opplysning: Tilleggsopplysning,
): String {
    val idColumn = if (id != null) "id, " else ""
    val idValue = if (id != null) "$id, " else ""

    return """
        INSERT INTO skattekort_tilleggsopplysning ($idColumn skattekort_id, opplysning)
        VALUES ($idValue  $skattekortId, '${opplysning.value}');
        """.trimIndent()
}

fun aSkattekortData(
    id: Long? = null,
    dataMottatt: String,
    inntektsaar: Int,
    fnr: String,
    skattekortId: Long? = null,
): String {
    val idColumn = if (id != null) "id, " else ""
    val idValue = if (id != null) "$id, " else ""
    val escapedJson = dataMottatt.replace("'", "''")
    return """
        INSERT INTO skattekort_data ($idColumn data_mottatt, inntektsaar, fnr, skattekort_id)
        VALUES ($idValue CAST('$escapedJson' AS JSON), $inntektsaar, '$fnr', ${skattekortId ?: "NULL"});
        """.trimIndent()
}

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
    utstedtDato = LocalDate.parse("2021-01-01"),
    kilde = "foo",
)

fun anArbeidstaker(
    resultat: ResultatForSkattekort,
    fnr: String,
    inntektsaar: Int,
    tilleggsopplysninger: List<Tilleggsopplysning>? = null,
    skattekort: Sskattekort? = null,
): Arbeidstaker =
    Arbeidstaker(
        arbeidstakeridentifikator = fnr,
        resultatForSkattekort = resultat.value,
        skattekort = skattekort,
        tilleggsopplysning = tilleggsopplysninger?.map { it.value },
        inntektsaar = inntektsaar,
    )

fun aHentSkattekortResponse(
    vararg arbeidstakere: Arbeidstaker,
    response: ResponseStatus = ResponseStatus.FORESPOERSEL_OK,
): HentSkattekortResponse =
    HentSkattekortResponse(
        status = response.name,
        arbeidsgiver =
            listOf(
                Arbeidsgiver(
                    arbeidsgiveridentifikator =
                        Arbeidsgiveridentifikator(
                            organisasjonsnummer = "312978083",
                        ),
                    arbeidstaker = arbeidstakere.toList(),
                ),
            ),
    )

fun databaseHas(vararg strings: String) {
    runThisSql(strings.joinToString("\n"))
}

fun aPerson(personId: Long) =
    """
        INSERT INTO personer(id) VALUES ($personId);            
    """

fun afoedselsnummer(
    personId: Long,
    fnr: String,
) = """
        INSERT INTO foedselsnumre(person_id, fnr)
            VALUES ($personId, '$fnr');
    """

fun aBestillingsbatch(
    id: Long,
    ref: String,
    status: BestillingsbatchStatus,
    type: BestillingsbatchType = BestillingsbatchType.BESTILLING,
) = """
        INSERT INTO bestillingsbatcher (id, bestillingsreferanse, data_sendt, status, type)
            VALUES ($id, '$ref', '{}', '$status', '${type.name}');
    """

fun aBestilling(
    personId: Long,
    fnr: String,
    inntektsaar: Int,
    batchId: Long?,
) = """
    INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
                    VALUES ($personId, '$fnr', $inntektsaar, $batchId);
    """.trimIndent()

fun anAbonnement(
    forespoerselId: Long,
    personId: Long,
    inntektsaar: Int,
    forsystem: Forsystem = Forsystem.OPPDRAGSSYSTEMET,
) = """
    INSERT INTO forespoersler(id, data_mottatt, forsystem)
        SELECT $forespoerselId, '${forsystem.value}:$inntektsaar:' || fnr, '${forsystem.value}' FROM foedselsnumre WHERE person_id = $personId;
    
    INSERT INTO abonnementer(forespoersel_id, person_id, inntektsaar)
                    VALUES ($forespoerselId, $personId, $inntektsaar);
    """.trimIndent()

fun aDbForskuddstrekk(
    id: Long,
    skattekortId: Long,
    type: String,
    trekkode: Trekkode,
    prosentSats: Double? = null,
    antMndForTrekk: Double? = null,
    tabellNummer: String? = null,
    frikortbeløp: Int? = null,
) = """
    INSERT INTO forskuddstrekk (id, skattekort_id, type, trekk_kode, prosentsats, antall_mnd_for_trekk, tabell_nummer, frikort_beloep)
    VALUES ($id, $skattekortId, '$type', '${trekkode.value}', ${prosentSats ?: "NULL"}, ${antMndForTrekk ?: "NULL"}, ${tabellNummer?.let { "'$it'" } ?: "NULL"}, ${frikortbeløp ?: "NULL"});
    """.trimIndent()

fun anUtsending(
    fnr: String,
    inntektsaar: Int,
    forsystem: String,
) = """
    INSERT INTO utsendinger (fnr, inntektsaar, forsystem)
    VALUES ('$fnr', $inntektsaar, '$forsystem');
    """.trimIndent()

fun aBatch(
    id: Long,
    status: BestillingsbatchStatus,
    type: BestillingsbatchType,
    bestillingsreferanse: String,
): Bestillingsbatch =
    Bestillingsbatch(
        id = BestillingsbatchId(id),
        status = status,
        type = type,
        bestillingsreferanse = bestillingsreferanse,
        oppdatert = Instant.MIN,
        opprettet = Instant.MIN,
        dataSendt = "",
    )

fun List<Skattekort>.shouldBeFunctionallyEquivalentTo(expected: List<Skattekort>) {
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
}

fun aBestillingsbatchWithJson(
    id: Long,
    ref: String,
    status: BestillingsbatchStatus,
    type: BestillingsbatchType = BestillingsbatchType.BESTILLING,
    dataSendt: String = """{"sendt":"$ref"}""",
    dataMottatt: String? = null,
) = """
    INSERT INTO bestillingsbatcher (id, bestillingsreferanse, data_sendt, data_mottatt, status, type)
        VALUES (
            $id,
            '$ref',
            '$dataSendt'::json,
            ${dataMottatt?.let { "'$it'::json" } ?: "NULL"},
            '${status.name}',
            '${type.name}'
        );
    """.trimIndent()
