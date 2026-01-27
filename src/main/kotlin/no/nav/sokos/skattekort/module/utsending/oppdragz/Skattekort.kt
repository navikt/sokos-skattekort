package no.nav.sokos.skattekort.module.utsending.oppdragz

import javax.xml.datatype.XMLGregorianCalendar

import no.nav.sokos.skattekort.module.skattekort.Forskuddstrekk

data class Skattekort(
    val inntektsaar: Long,
    val utstedtDato: XMLGregorianCalendar?,
    val skattekortidentifikator: Long?,
    val forskuddstrekk: List<Forskuddstrekk> = listOf(),
)
