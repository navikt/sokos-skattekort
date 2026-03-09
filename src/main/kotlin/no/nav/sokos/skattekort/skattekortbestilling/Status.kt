package no.nav.sokos.skattekort.skattekortbestilling

enum class Status {
    IKKE_FORESPURT,
    UGYLDIG_FNR,
    IKKE_BESTILT,
    BESTILT,
    FEILET_I_BESTILLING,
    VENTER_PAA_UTSENDING,
    SENDT_FORSYSTEM,
    UGYLDIG_FORSYSTEM,
}
