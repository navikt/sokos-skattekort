package no.nav.sokos.skattekort.skattekort

import java.math.BigDecimal

import kotlin.time.ExperimentalTime

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

import no.nav.sokos.skattekort.person.PersonId

@OptIn(ExperimentalTime::class)
class SyntetiseringTest :
    BehaviorSpec({
        Given("en bruker som ikke er trekkpliktig") {
            fun ikkeTrekkpliktigSkattekort() =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.IkkeTrekkplikt,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = 2025,
                    kilde = "BOGUS",
                )

            When("skattekortet syntetiseres") {
                Then("lages frikort for relevante trekkoder") {
                    val sk = ikkeTrekkpliktigSkattekort()
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
            }
        }

        Given("skattekort med opphold på Svalbard") {
            fun svalbardSkattekort(inntektsaar: Int) =
                Skattekort(
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    personId = PersonId(1),
                    utstedtDato = null,
                    identifikator = null,
                    inntektsaar = inntektsaar,
                    kilde = "BOGUS",
                    tilleggsopplysningList = listOf(Tilleggsopplysning.OPPHOLD_PAA_SVALBARD),
                )

            When("skattekortet gjelder 2025") {
                Then("lages default-trekk for Svalbard") {
                    val sk = svalbardSkattekort(2025)
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
            }

            When("skattekortet gjelder 2026") {
                Then("lages default-trekk med oppdaterte satser for Svalbard") {
                    val sk = svalbardSkattekort(2026)
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
            }
        }

        Given("skattekort med kildeskatt på pensjon") {
            When("skattekortet allerede har alle nødvendige trekkoder") {
                Then("skal skattekortet ikke røres") {
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
            }

            When("PENSJON_FRA_NAV mangler") {
                Then("fylles manglende trekkode inn uten å endre resten") {
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
            }

            When("PENSJON_FRA_NAV og UFOERETRYGD_FRA_NAV mangler") {
                Then("fylles begge manglende trekkoder inn uten å endre resten") {
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
            }

            When("UFOERETRYGD_FRA_NAV mangler") {
                Then("fylles manglende trekkode inn uten å endre resten") {
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
            }
        }
    })
