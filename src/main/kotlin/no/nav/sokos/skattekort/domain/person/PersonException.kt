package no.nav.sokos.skattekort.domain.person

class PersonException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
