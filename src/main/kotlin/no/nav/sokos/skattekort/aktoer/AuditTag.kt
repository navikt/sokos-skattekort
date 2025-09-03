package no.nav.sokos.skattekort.aktoer

enum class AuditTag(
    private val value: String,
) {
    OPPRETTET_AKTOER("OPPRETTET_AKTOER"),
    ;

    companion object {
        fun fromValue(value: String): AuditTag = AuditTag.entries.first { it.value == value }
    }
}
