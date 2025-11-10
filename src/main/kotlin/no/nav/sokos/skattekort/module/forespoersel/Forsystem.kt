package no.nav.sokos.skattekort.module.forespoersel

enum class Forsystem(
    val value: String,
) {
    OPPDRAGSSYSTEMET("OS"),
    MANUELL("MANUELL"),
    ;

    companion object {
        fun fromValue(value: String): Forsystem = entries.first { it.value == value }
    }
}
