package no.nav.sokos.skattekort.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AccessPolicyTest :
    BehaviorSpec({
        Given("tilgangspolicyens konfigurerte scopes") {
            When("tillatte scopes sammenlignes med Scope-enum") {
                Then("skal ALLOWED_SCOPES matche verdiene i Scope-enum") {
                    AccessPolicy.ALLOWED_SCOPES.toList().sorted() shouldContainExactly
                        Scope.entries.map { it.value }.sorted()
                }
            }
        }

        Given("tilgangspolicyens konfigurerte roller") {
            When("tillatte roller sammenlignes med Role-enum") {
                Then("skal ALLOWED_ROLES matche verdiene i Role-enum") {
                    AccessPolicy.ALLOWED_ROLES.toList().sorted() shouldContainExactly
                        Role.entries.map { it.value }.sorted()
                }
            }
        }

        Given("hasRequiredScope") {
            When("påkrevd scope finnes og er tillatt") {
                Then("skal true returneres") {
                    AccessPolicy.hasRequiredScope(
                        scopes = listOf("other.scope", Scope.HENT_SKATTEKORT_SCOPE.value),
                        requiredScope = Scope.HENT_SKATTEKORT_SCOPE.value,
                    ) shouldBe true
                }
            }

            When("påkrevd scope mangler") {
                Then("skal false returneres") {
                    AccessPolicy.hasRequiredScope(
                        scopes = listOf("other.scope"),
                        requiredScope = Scope.HENT_SKATTEKORT_SCOPE.value,
                    ) shouldBe false
                }
            }

            When("påkrevd scope finnes men ikke er tillatt") {
                Then("skal false returneres") {
                    AccessPolicy.hasRequiredScope(
                        scopes = listOf("custom.read"),
                        requiredScope = "custom.read",
                    ) shouldBe false
                }
            }

            When("scope-listen er tom") {
                Then("skal false returneres") {
                    AccessPolicy.hasRequiredScope(
                        scopes = emptyList(),
                        requiredScope = Scope.HENT_SKATTEKORT_SCOPE.value,
                    ) shouldBe false
                }
            }
        }

        Given("hasRequiredRole") {
            When("påkrevd rolle finnes og er tillatt") {
                Then("skal true returneres") {
                    AccessPolicy.hasRequiredRole(
                        roles = listOf("other.role", Role.HENT_SKATTEKORT_ROLE.value),
                        requiredRole = Role.HENT_SKATTEKORT_ROLE.value,
                    ) shouldBe true
                }
            }

            When("påkrevd rolle mangler") {
                Then("skal false returneres") {
                    AccessPolicy.hasRequiredRole(
                        roles = listOf("other.role"),
                        requiredRole = Role.HENT_SKATTEKORT_ROLE.value,
                    ) shouldBe false
                }
            }

            When("påkrevd rolle finnes men ikke er tillatt") {
                Then("skal false returneres") {
                    AccessPolicy.hasRequiredRole(
                        roles = listOf("custom.role"),
                        requiredRole = "custom.role",
                    ) shouldBe false
                }
            }

            When("rolle-listen er tom") {
                Then("skal false returneres") {
                    AccessPolicy.hasRequiredRole(
                        roles = emptyList(),
                        requiredRole = Role.HENT_SKATTEKORT_ROLE.value,
                    ) shouldBe false
                }
            }
        }
    })
