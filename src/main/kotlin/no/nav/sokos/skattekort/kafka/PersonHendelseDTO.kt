package no.nav.sokos.skattekort.kafka

data class PersonHendelseDTO(
    val hendelseId: String,
    val personidenter: List<String>,
    val opplysningstype: String,
    val endringstype: EndringstypeDTO,
    val folkeregisteridentifikator: FolkeregisteridentifikatorDTO,
)

data class FolkeregisteridentifikatorDTO(
    val identifikasjonsnummer: String,
    val type: String,
    val status: String,
)

enum class EndringstypeDTO {
    OPPRETTET,
    KORRIGERT,
    ANNULLERT,
    OPPHOERT,
}
