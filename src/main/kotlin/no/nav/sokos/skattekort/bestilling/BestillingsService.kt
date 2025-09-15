package no.nav.sokos.skattekort.bestilling

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.person.PersonService

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class BestillingsService(
    val db: HikariDataSource,
    val personService: PersonService,
)
