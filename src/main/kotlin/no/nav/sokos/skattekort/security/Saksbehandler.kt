package no.nav.sokos.skattekort.security

data class Saksbehandler(
    val ident: String,
    val roller: List<String> = emptyList(),
)
