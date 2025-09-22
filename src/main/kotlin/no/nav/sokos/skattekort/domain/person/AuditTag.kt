package no.nav.sokos.skattekort.domain.person

enum class AuditTag(
    private val value: String,
) {
    OPPRETTET_PERSON("OPPRETTET_PERSON"),
    ;

    companion object {
        fun fromValue(value: String): AuditTag = entries.first { it.value == value }
    }
}
