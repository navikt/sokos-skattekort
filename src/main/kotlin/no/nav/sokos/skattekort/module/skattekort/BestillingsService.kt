package no.nav.sokos.skattekort.module.bestilling

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.module.person.PersonService

// TODO: Metrikk: bestillinger per system
// TODO: Metrikk for varsling: tid siden siste mottatte bestilling
// TODO: Metrikk: Eldste bestilling i databasen som ikke er fullført.
class BestillingsService(
    val dataSource: HikariDataSource,
    val personService: PersonService,
)
