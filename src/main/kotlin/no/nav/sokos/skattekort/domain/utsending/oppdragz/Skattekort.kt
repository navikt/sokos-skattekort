package no.nav.sokos.skattekort.domain.utsending.oppdragz

import javax.xml.datatype.XMLGregorianCalendar

data class Skattekort(
    val inntektsaar: Long,
    val utstedtDato: XMLGregorianCalendar,
    val skattekortidentifikator: Long,
    val forskuddstrekk: List<Forskuddstrekk> = listOf(),
)
