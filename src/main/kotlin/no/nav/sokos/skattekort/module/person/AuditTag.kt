package no.nav.sokos.skattekort.module.person

import mu.KotlinLogging

enum class AuditTag(
    private val value: String,
) {
    OPPRETTET_PERSON("OPPRETTET_PERSON"),
    MOTTATT_FORESPOERSEL("MOTTATT_FORESPOERSEL"),
    UTSENDING_FEILET("UTSENDING_FEILET"),
    UTSENDING_OK("UTSENDING_OK"),
    BESTILLING_SENDT("BESTILLING_SENDT"),
    BESTILLING_FEILET("BESTILLING_FEILET"),
    BESTILLING_ETTERLATT("BESTILLING_ETTERLATT"),
    SKATTEKORTINFORMASJON_MOTTATT("SKATTEKORTINFORMASJON_MOTTATT"),
    HENTING_AV_SKATTEKORT_FEILET("HENTING_AV_SKATTEKORT_FEILET"),
    OPPDATERT_PERSONIDENTIFIKATOR("OPPDATERT_PERSONIDENTIFIKATOR"),
    SYNTETISERT_SKATTEKORT("SYNTETISERT_SKATTEKORT"),
    INVALID_FNR("INVALID_FNR"),
    UVENTET_PERSON("UVENTET_PERSON"),
    UKJENT("UKJENT"),
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
