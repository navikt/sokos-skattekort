package no.nav.sokos.skattekort.skattekort

import java.time.LocalDateTime
import java.time.Month

object ReglerForInntektsaar {
    fun skalBestilleForNesteAarOgsaa(year: Int): Boolean {
        val now = LocalDateTime.now()
        return now.year == year && lovligeInntektsAarAaBestilleFraSkatteetaten().contains(year + 1)
    }

    fun lovligeInntektsAarAaBestilleFraSkatteetaten(): List<Int> {
        val now = LocalDateTime.now()
        val min = if (now.month <= Month.JUNE) now.year - 1 else now.year
        return (min..maxInntektsaar()).map { it }.toList()
    }

    fun inntektsaarAaBestille(): List<Int> = lovligeInntektsAarAaBestilleFraSkatteetaten().filter { it >= LocalDateTime.now().year }

    fun alleLovligeInntektsaarAaHenteSkattekortFor(): List<Int> {
        val now = LocalDateTime.now()
        val min = now.year - 1
        return (min..maxInntektsaar()).toList()
    }

    fun maxInntektsaar(): Int {
        val now = LocalDateTime.now()
        return if (now.dayOfMonth >= 15 && now.month == Month.DECEMBER) {
            (now.year + 1)
        } else {
            now.year
        }
    }
}
