package no.nav.sokos.skattekort.module.forespoersel

enum class Forsystem(
    val value: String,
) {
    OPPDRAGSSYSTEMET("OS"),
    OPPDRAGSSYSTEMET_STOR("OS_STOR"),
    MANUELL("MANUELL"),
    ;

    companion object {
        fun fromValue(value: String): Forsystem = entries.first { it.value == value }
    }
}
