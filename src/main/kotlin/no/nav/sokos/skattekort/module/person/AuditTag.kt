package no.nav.sokos.skattekort.module.person

import mu.KotlinLogging

enum class AuditTag(
    private val value: String,
) {
    BESTILLING_ETTERLATT("BESTILLING_ETTERLATT"),
    BESTILLING_FEILET("BESTILLING_FEILET"),
    BESTILLING_SENDT("BESTILLING_SENDT"),
    HENTING_AV_SKATTEKORT_FEILET("HENTING_AV_SKATTEKORT_FEILET"),
    INVALID_FNR("INVALID_FNR"),
    MOTTATT_FORESPOERSEL("MOTTATT_FORESPOERSEL"),
    NYTT_FNR("NYTT_FNR"),
    OPPDATERT_PERSONIDENTIFIKATOR("OPPDATERT_PERSONIDENTIFIKATOR"),
    OPPRETTET_PERSON("OPPRETTET_PERSON"),
    SKATTEKORTINFORMASJON_MOTTATT("SKATTEKORTINFORMASJON_MOTTATT"),
    SYNTETISERT_SKATTEKORT("SYNTETISERT_SKATTEKORT"),
    UKJENT("UKJENT"),
    UTSENDING_FEILET("UTSENDING_FEILET"),
    UTSENDING_OK("UTSENDING_OK"),
    UVENTET_PERSON("UVENTET_PERSON"),
    MANUELL("MANUELL"), // For manuell databasepatching
    ;

    companion object {
        private val logger = KotlinLogging.logger {}

        fun fromValue(value: String): AuditTag =
            runCatching { entries.first { it.value == value } }
                .onFailure {
                    logger.error("Ukjent AuditTag-verdi $value")
                    UKJENT
                }.getOrThrow()
    }
}
