package no.nav.sokos.skattekort.skattekortbestilling.status

enum class Status {
    IKKE_FORESPURT,
    UGYLDIG_FNR,
    IKKE_BESTILT,
    BESTILT,
    FEILET_I_BESTILLING,
    SKJERMET,
    VENTER_PAA_UTSENDING,
    FERDIG_BEHANDLET,
    UGYLDIG_FORSYSTEM,
    UKJENT,
}
