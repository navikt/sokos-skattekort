package no.nav.sokos.skattekort.skattekort

import java.math.BigDecimal

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

import no.nav.sokos.skattekort.person.PersonId

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
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
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
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainExactly
                        listOf<Forskuddstrekk>(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal("15.70"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("13.10"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("15.70"),
                            ),
                        )
                }
            }
        }
        test("Lag default-trekk for svalbard med oppdaterte satser for 2026") {
            val sk =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2026,
                    kilde = "BOGUS",
                    tilleggsopplysningList = listOf(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD),
                )
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainExactly
                        listOf<Forskuddstrekk>(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal("15.60"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("13.10"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("15.60"),
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
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
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
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainAll
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("15.00"),
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
                                prosentSats = BigDecimal("200.00"),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainAll
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("15.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("15.00"),
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
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                        ),
                    tilleggsopplysningList = listOf(Tilleggsopplysning.KILDESKATT_PAA_PENSJON),
                )
            val resultat: Pair<Skattekort, String>? =
                Syntetisering.evtSyntetiserSkattekort(
                    skattekort = sk,
                    id =
                        SkattekortId(
                            1,
                        ),
                )
            assertSoftly {
                resultat shouldNotBeNull {
                    first.forskuddstrekkList shouldContainAll
                        listOf(
                            Prosentkort(
                                trekkode = Trekkode.LOENN_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.UFOERETRYGD_FRA_NAV,
                                prosentSats = BigDecimal("15.00"),
                            ),
                            Prosentkort(
                                trekkode = Trekkode.PENSJON_FRA_NAV,
                                prosentSats = BigDecimal("200.00"),
                            ),
                        )
                }
            }
        }
    })
