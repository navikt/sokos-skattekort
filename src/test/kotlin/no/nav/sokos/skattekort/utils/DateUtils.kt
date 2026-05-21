package no.nav.sokos.skattekort.utils

import java.time.Instant
import java.time.ZoneId

object DateUtils {
    private val localZoneId = ZoneId.of("Europe/Oslo")

    fun Instant.toLocalDate() = this.atZone(localZoneId).toLocalDate()
}
