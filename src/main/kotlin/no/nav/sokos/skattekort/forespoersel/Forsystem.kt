package no.nav.sokos.skattekort.forespoersel

enum class Forsystem(
    val kode: String,
) {
    OPPDRAGSSYSTEMET("OS"),
    ARENA("ARENA"),
    ;

    companion object {
        fun fromValue(value: String): Forsystem = Forsystem.entries.first { it.kode == value }

        fun fromMessage(message: String): Forsystem =
            when {
                // samme sjekk som i OS-Eskatt
                message.startsWith("<") -> ARENA
                !message.startsWith("<") -> OPPDRAGSSYSTEMET
                else -> throw IllegalArgumentException("Ukjent forsystem i melding: $message")
            }
    }
}
