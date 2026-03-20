package no.nav.sokos.skattekort.infrastructure.skatteetaten

import java.math.BigDecimal

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

import no.nav.sokos.skattekort.api.model.WrappedWithErrorResponse
import no.nav.sokos.skattekort.dto.v2.SkattekortDTO
import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Forskuddstrekk
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Skattekort
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Trekkprosent
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort.SkattekortopplysningerOK
import no.nav.sokos.skattekort.skattekort.Trekkode.LOENN_FRA_NAV
import no.nav.sokos.skattekort.skattekort.Trekkode.UFOERETRYGD_FRA_NAV
import no.nav.sokos.skattekort.skattekort.anArbeidstaker

object SkatteetatenClientTestUtils {
    fun String.toBestillSkattekortResponse() = Json.decodeFromString(BestillSkattekortResponse.serializer(), this)

    fun String.toSkattekortDTOWrappedWithErrorResponse() =
        Json.decodeFromString(
            WrappedWithErrorResponse.serializer(ListSerializer(SkattekortDTO.serializer())),
            this,
        )

    fun String.toStringWrappedWithErrorResponse() = Json.decodeFromString(WrappedWithErrorResponse.serializer(String.serializer()), this)

    fun okBestillSkattekortResponse(ref: String): BestillSkattekortResponse =
        """
        {
          "dialogreferanse": "any-dialog-ref",
          "bestillingsreferanse": "$ref"
        }    
        """.trimIndent().toBestillSkattekortResponse()

    fun aSkattekortFor(
        fnr: String,
        id: Long,
    ) = anArbeidstaker(
        resultat = SkattekortopplysningerOK,
        fnr = fnr,
        inntektsaar = 2025,
        skattekort =
            Skattekort(
                utstedtDato = "2025-11-01",
                skattekortidentifikator = id,
                forskuddstrekk =
                    listOf(
                        Forskuddstrekk(
                            trekkode = LOENN_FRA_NAV.value,
                            trekkprosent = Trekkprosent(BigDecimal("25.00")),
                        ),
                        Forskuddstrekk(
                            trekkode = UFOERETRYGD_FRA_NAV.value,
                            trekkprosent = Trekkprosent(BigDecimal("28.00")),
                        ),
                    ),
            ),
    )
}
