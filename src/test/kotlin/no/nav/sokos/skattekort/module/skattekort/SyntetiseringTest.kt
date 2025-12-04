package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal

import kotlin.time.ExperimentalTime

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

import no.nav.sokos.skattekort.module.person.PersonId

@OptIn(ExperimentalTime::class)
class SyntetiseringTest :
    FunSpec({
        test("Lag frikort for ikke trekkpliktige") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.IkkeTrekkplikt,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                )
            val resultat: Pair<Skattekort, String>? = Syntetisering.evtSyntetiserSkattekort(skattekort = sk, id = SkattekortId(1))
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainExactly
                        listOf(
                            Frikort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                frikortBeloep = null,
                            ),
                            Frikort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                frikortBeloep = null,
                            ),
                            Frikort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                frikortBeloep = null,
                            ),
                        )
                }
            }
        }
        test("Lag default-trekk for svalbard") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                    tilleggsopplysningList = listOf(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD),
                )
            val resultat: Pair<Skattekort, String>? = Syntetisering.evtSyntetiserSkattekort(skattekort = sk, id = SkattekortId(1))
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainExactly
                        listOf<Forskuddstrekk>(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(15.70),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(15.70),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(13.10),
                            ),
                        )
                }
            }
        }
        test("Ikke rør skattekort for kildeskatt pensjon") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                    forskuddstrekkList =
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? = Syntetisering.evtSyntetiserSkattekort(skattekort = sk, id = SkattekortId(1))
            assertSoftly {
                resultat.shouldBeNull()
            }
        }
        test("Fyll inn manglende PENSJON_FRA_NAV for kildeskatt, ikke rør resten") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                    forskuddstrekkList =
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? = Syntetisering.evtSyntetiserSkattekort(skattekort = sk, id = SkattekortId(1))
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainAll
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(15.0),
                            ),
                        )
                }
            }
        }
        test("Fyll inn manglende PENSJON_FRA_NAV og UFOERETRYGD_FRA_NAV for kildeskatt, ikke rør resten") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                    forskuddstrekkList =
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? = Syntetisering.evtSyntetiserSkattekort(skattekort = sk, id = SkattekortId(1))
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainAll
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(15.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(15.0),
                            ),
                        )
                }
            }
        }
        test("Fyll inn manglende UFOERETRYGD_FRA_NAV for kildeskatt, ikke rør resten") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                    forskuddstrekkList =
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? = Syntetisering.evtSyntetiserSkattekort(skattekort = sk, id = SkattekortId(1))
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainAll
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(15.0),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal.valueOf(200.0),
                            ),
                        )
                }
            }
        }
    })
