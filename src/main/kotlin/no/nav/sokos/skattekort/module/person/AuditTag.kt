package no.nav.sokos.skattekort.module.person

enum class AuditTag(
    private val value: String,
) {
    OPPRETTET_PERSON("OPPRETTET_PERSON"),
    MOTTATT_FORESPOERSEL("MOTTATT_FORESPOERSEL"),
    UTSENDING_FEILET("UTSENDING_FEILET"),
    UTSENDING_OK("UTSENDING_OK"),
    BESTILLING_SENDT("BESTILLING_SENDT"),
    BESTILLING_FEILET("BESTILLING_FEILET"),
    SKATTEKORTINFORMASJON_MOTTATT("SKATTEKORTINFORMASJON_MOTTATT"),
    HENTING_AV_SKATTEKORT_FEILET("HENTING_AV_SKATTEKORT_FEILET"),
    ;

    companion object {
        fun fromValue(value: String): AuditTag = entries.first { it.value == value }
    }
}
