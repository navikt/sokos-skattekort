package no.nav.sokos.skattekort.module.utsending.oppdragz

enum class Resultatstatus(
    val value: String,
) {
    IKKE_SKATTEKORT("ikkeSkattekort"),

    // VURDER_ARBEIDSTILLATELSE("vurderArbeidstillatelse"), Ikke lenger i API
    IKKE_TREKKPLIKT("ikkeTrekkplikt"),
    SKATTEKORTOPPLYSNINGER_OK("skattekortopplysningerOK"),
    UGYLDIG_ORGANISASJONSNUMMER("ugyldigOrganisasjonsnummer"),
    UGYLDIG_FOEDSELS_ELLER_DNUMMER("ugyldigFoedselsEllerDnummer"),
    UTGAATT_DNUMMER_SKATTEKORT_FOR_FOEDSELSNUMMER_ER_LEVERT("utgaattDnummerSkattekortForFoedselsnummerErLevert"),
    ;

    companion object {
        fun fromValue(value: String): Resultatstatus = Resultatstatus.entries.first { it.value == value }
    }
}
