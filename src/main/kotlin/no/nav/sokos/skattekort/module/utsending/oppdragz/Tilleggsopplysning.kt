package no.nav.sokos.skattekort.module.utsending.oppdragz

import mu.KotlinLogging

enum class Tilleggsopplysning(
    val value: String,
) {
    OPPHOLD_PAA_SVALBARD("oppholdPaaSvalbard"),
    KILDESKATTPENSJONIST("kildeskattpensjonist"),
    OPPHOLD_I_TILTAKSSONE("oppholdITiltakssone"),
    KILDESKATT_PAA_LOENN("kildeskattPaaLoenn"),
    ;

    companion object {
        private val logger = KotlinLogging.logger {}

        fun fromValue(value: String): Tilleggsopplysning {
            if (value.equals("kildeskattPaaPensjon")) {
                return Tilleggsopplysning.KILDESKATTPENSJONIST // TODO: Sjekk med Endre at dette er faktisk det de forventer...
            }
            try {
                return Tilleggsopplysning.entries.first { it.value == value }
            } catch (e: NoSuchElementException) {
                logger.error("Ukjent tilleggsopplysning funnet: $value")
                throw e
            }
        }
    }
}
