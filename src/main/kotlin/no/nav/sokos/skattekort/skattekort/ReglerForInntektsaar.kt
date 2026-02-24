package no.nav.sokos.skattekort.skattekort

import java.time.LocalDateTime
import java.time.Month

object ReglerForInntektsaar {
    fun lovligeInntektsAarAaBestilleFraSkatteetaten(): List<Short> {
        val now = LocalDateTime.now()
        val min = if (now.month <= Month.JUNE) now.year - 1 else now.year
        return (min..maxInntektsaar()).map { it.toShort() }.toList()
    }

    fun inntektsaarAaBestille(): List<Short> = lovligeInntektsAarAaBestilleFraSkatteetaten().filter { it >= LocalDateTime.now().year }

    fun alleLovligeInntektsaarAaHenteSkattekortFor(): List<Short> {
        val now = LocalDateTime.now()
        val min = now.year - 1
        return (min..maxInntektsaar()).map { it.toShort() }.toList()
    }

    fun maxInntektsaar(): Short {
        val now = LocalDateTime.now()
        return if (now.dayOfMonth >= 15 && now.month == Month.DECEMBER) {
            (now.year + 1).toShort()
        } else {
            now.year.toShort()
        }
    }
}
