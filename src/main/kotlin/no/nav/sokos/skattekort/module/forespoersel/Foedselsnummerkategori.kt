package no.nav.sokos.skattekort.module.forespoersel

import kotlinx.datetime.LocalDate

enum class Foedselsnummerkategori(
    val value: String,
    val erGyldig: (String) -> Boolean,
    val kanBestilleSkattekort: (String) -> Boolean,
) {
    GYLDIGE(value = "GYLDIGE", erGyldig = ::erGyldigFnrEllerDnr, kanBestilleSkattekort = ::erGyldigFnrEllerDnr),
    KUNSTIGE_FNR(value = "KUNSTIGE_FNR", erGyldig = ::erDollyfnr or ::erTenorFnr, kanBestilleSkattekort = ::erTenorFnr),
    ALLE(value = "ALLE", erGyldig = ::lengdeOgTallRegel, kanBestilleSkattekort = ::lengdeOgTallRegel),
}

fun erGyldigFnrEllerDnr(fnr: String): Boolean =
    (
        lengdeOgTallRegel(fnr) &&
            (
                isDateParseable(fnr) ||
                    isDateParseable(fnr, dayOffset = 40)
            )
    )

fun erDollyfnr(fnr: String): Boolean =
    (
        lengdeOgTallRegel(fnr) &&
            isDateParseable(fnr, monthOffset = 40) ||
            isDateParseable(fnr, dayOffset = 40, monthOffset = 40)
    )

fun erTenorFnr(fnr: String): Boolean =
    (
        lengdeOgTallRegel(fnr) &&
            isDateParseable(fnr, monthOffset = 80) ||
            isDateParseable(fnr, dayOffset = 40, monthOffset = 80)
    )

fun lengdeOgTallRegel(fnr: String): Boolean = (Regex("^[0-9]{11}$").matches(fnr))

infix fun ((String) -> Boolean).or(other: (String) -> Boolean): (String) -> Boolean =
    { s ->
        this(s) || other(s)
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
