package no.nav.sokos.skattekort.skattekortbestilling.status

enum class Status {
    UGYLDIG_FNR,
    UGYLDIG_FORSYSTEM,
    SKJERMET,
    IKKE_FORESPURT,
    IKKE_BESTILT,
    BESTILT,
    FEILET_I_BESTILLING,
    VENTER_UTSENDING,
    ABONNERER,
    ABONNERER_IKKE,
    UKJENT,
}
