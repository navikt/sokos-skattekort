package no.nav.sokos.skattekort.skattekortbestilling.status

enum class Status {
    IKKE_FORESPURT,
    UGYLDIG_FNR,
    IKKE_BESTILT,
    BESTILT,
    VENTER_PAA_MANUELT_SKATTEKORT,
    FEILET_I_BESTILLING,
    SKJERMET,
    VENTER_PAA_UTSENDING,
    UGYLDIG_FORSYSTEM,
    UKJENT,
    ABONNERER,
    ABONNERER_IKKE,
}
