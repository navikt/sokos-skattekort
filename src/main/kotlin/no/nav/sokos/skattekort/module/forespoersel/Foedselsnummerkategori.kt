package no.nav.sokos.skattekort.module.forespoersel

import kotlinx.datetime.LocalDate

enum class Foedselsnummerkategori(
    val value: String,
    val regel: (String) -> Boolean,
) {
    GYLDIGE("GYLDIGE", ::gyldigFnrEllerDnrRegel),
    TENOR("TENOR", ::tenorRegel),
    ALLE("ALLE", ::lengdeOgTallRegel),
}

fun gyldigFnrEllerDnrRegel(fnr: String): Boolean =
    lengdeOgTallRegel(fnr) &&
        (
            isDateParseable(fnr) ||
                isDateParseable(fnr, dayOffset = 40)
        )

fun tenorRegel(fnr: String): Boolean =
    lengdeOgTallRegel(fnr) &&
        isDateParseable(fnr, monthOffset = 80)

fun lengdeOgTallRegel(fnr: String): Boolean {
    val regex = Regex("^[0-9]{11}$")
    return regex.matches(fnr)
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
