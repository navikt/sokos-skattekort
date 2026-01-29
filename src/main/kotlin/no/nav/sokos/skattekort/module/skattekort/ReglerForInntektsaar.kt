package no.nav.sokos.skattekort.module.skattekort

import java.time.LocalDateTime.now

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.toKotlinLocalDateTime

object ReglerForInntektsaar {
    fun lovligeInntektsAarAaBestilleFraSkatteetaten(): List<Short> {
        val now = now().toKotlinLocalDateTime()
        val min = if (now.month <= Month.JUNE) now.year - 1 else now.year
        return (min..maxInntektsaar(now)).map { it.toShort() }.toList()
    }

    fun inntektsaarAaBestille(): List<Short> = lovligeInntektsAarAaBestilleFraSkatteetaten().filter { it >= now().year }

    fun alleLovligeInntektsaarAaHenteSkattekortFor(): List<Short> {
        val now = now().toKotlinLocalDateTime()
        val min = now.year - 1
        return (min..maxInntektsaar(now)).map { it.toShort() }.toList()
    }

    private fun maxInntektsaar(now: LocalDateTime): Short =
        if (now.day >= 15 && now.month == Month.DECEMBER) {
            (now.year + 1).toShort()
        } else {
            now.year.toShort()
        }
}
