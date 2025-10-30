package no.nav.sokos.skattekort.module.utsending.oppdragz

import javax.xml.datatype.XMLGregorianCalendar

data class Skattekort(
    val inntektsaar: Int,
    val utstedtDato: XMLGregorianCalendar,
    val skattekortidentifikator: Long,
    val forskuddstrekk: List<Forskuddstrekk> = listOf(),
)
