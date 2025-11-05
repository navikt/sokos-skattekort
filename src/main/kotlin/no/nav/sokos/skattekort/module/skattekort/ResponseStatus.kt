package no.nav.sokos.skattekort.module.skattekort

enum class ResponseStatus {
    FORESPOERSEL_OK,
    UGYLDIG_INNTEKTSAAR,
    UGYLDIG_FORESPOERSEL,
    INGEN_ENDRINGER,
    ENDRINGSFORESPOERSEL_UTEN_TIDLIGERE_BESTILLING,
}
