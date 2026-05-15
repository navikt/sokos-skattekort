package no.nav.sokos.skattekort.skattekortbestilling.status

enum class Status {
    IKKE_FORESPURT,
    KUNSTIG_FNR,
    UGYLDIG_FNR,
    IKKE_BESTILT,
    BESTILT,
    FEILET_I_BESTILLING,
    MANGLER_SKATTEKORT,
    VENTER_PAA_UTSENDING,
    SENDT_FORSYSTEM,
    UGYLDIG_FORSYSTEM,
    UKJENT,
}
