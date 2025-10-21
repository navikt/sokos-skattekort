package no.nav.sokos.skattekort.module.utsending

import com.zaxxer.hikari.HikariDataSource

class UtsendingService(
    private val dataSource: HikariDataSource,
)
