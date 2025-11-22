package no.nav.sokos.skattekort.module.forespoersel

import kotlinx.datetime.LocalDate

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER

private val logger = KotlinLogging.logger { }

enum class Foedselsnummerkategori(
    val value: String,
    val erGyldig: (String) -> Boolean,
) {
    GYLDIGE("GYLDIGE", ::gyldigFnrEllerDnrRegel),
    TENOR("TENOR", ::tenorRegel),
    ALLE("ALLE", ::lengdeOgTallRegel),
}

fun gyldigFnrEllerDnrRegel(fnr: String): Boolean =
    if (lengdeOgTallRegel(fnr) &&
        (
            isDateParseable(fnr) ||
                isDateParseable(fnr, dayOffset = 40)
        )
    ) {
        true
    } else {
        logger.warn(marker = TEAM_LOGS_MARKER) { "GyldigFnrEllerDnrRegel fant ugyldig fnr: $fnr" }
        false
    }

fun tenorRegel(fnr: String): Boolean =
    if (lengdeOgTallRegel(fnr) &&
        isDateParseable(fnr, monthOffset = 80)
    ) {
        true
    } else {
        logger.warn(marker = TEAM_LOGS_MARKER) { "TenorRegel fant ugyldig fnr: $fnr" }
        false
    }

fun lengdeOgTallRegel(fnr: String): Boolean {
    val regex = Regex("^[0-9]{11}$")
    return if (regex.matches(fnr)) {
        true
    } else {
        logger.warn(marker = TEAM_LOGS_MARKER) { "LengdeOgTallRegel fant ugyldig fnr: $fnr" }
        false
    }
}

private fun isDateParseable(
    fnr: String,
    dayOffset: Int = 0,
    monthOffset: Int = 0,
): Boolean =
    try {
        LocalDate(
            year = fnr.substring(4, 6).toInt(),
            month = fnr.substring(2, 4).toInt() - monthOffset,
            day = fnr.take(2).toInt() - dayOffset,
        )
        true
    } catch (_: IllegalArgumentException) {
        false
    }
