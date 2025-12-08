package no.nav.sokos.skattekort.module.skattekort

enum class Status {
    UKJENT,
    IKKE_FNR,
    IKKE_BESTILT,
    BESTILT,
    FEILET_I_BESTILLING,
    VENTER_PAA_UTSENDING,
    SENDT_FORSYSTEM,
}
