package no.nav.sokos.skattekort.person

enum class AuditTag(
    private val value: String,
) {
    OPPRETTET_AKTOER("OPPRETTET_AKTOER"),
    ;

    companion object {
        fun fromValue(value: String): AuditTag = AuditTag.entries.first { it.value == value }
    }
}
