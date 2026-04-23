package no.nav.sokos.skattekort.skattekortbestilling

@JvmInline
value class Bestillingsreferanse(
    val value: String,
) {
    init {
        require(value.matches(Regex("^[A-Z]{2,3}[0-9]{4,8}$"))) {
            "Bestillingsreferanse must be 2-3 letters A–Z followed by 4–8 digits 0–9"
        }
    }
}
