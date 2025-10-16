package no.nav.sokos.skattekort.module.utsending.oppdragz

enum class Tilleggsopplysning(
    val value: String,
) {
    OPPHOLD_PAA_SVALBARD("oppholdPaaSvalbard"),
    KILDESKATTPENSJONIST("kildeskattpensjonist"),
    OPPHOLD_I_TILTAKSSONE("oppholdITiltakssone"),
    KILDESKATT_PAA_LOENN("kildeskattPaaLoenn"),
    ;

    companion object {
        fun fromValue(value: String): Tilleggsopplysning = Tilleggsopplysning.entries.first { it.value == value }
    }
}
