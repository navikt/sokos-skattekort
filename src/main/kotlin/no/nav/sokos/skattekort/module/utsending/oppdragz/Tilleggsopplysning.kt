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
        fun fromValue(value: String): Tilleggsopplysning {
            if (value.equals("kildeskattPaaPensjon")) {
                return Tilleggsopplysning.KILDESKATTPENSJONIST // TODO: Sjekk med Endre at dette er faktisk det de forventer...
            }
            try {
                return Tilleggsopplysning.entries.first { it.value == value }
            } catch (e: NoSuchElementException) {
                println("Tilleggsopplysning feilet: $value")
                throw e
            }
        }
    }
}
