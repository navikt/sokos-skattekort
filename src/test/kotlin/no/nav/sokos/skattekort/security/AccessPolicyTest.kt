package no.nav.sokos.skattekort.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class AccessPolicyTest :
    FunSpec({
        test("ALLOWED_SCOPES should match Scope enum values") {
            AccessPolicy.ALLOWED_SCOPES.toList().sorted() shouldContainExactly
                Scope.entries.map { it.value }.sorted()
        }

        test("ALLOWED_ROLES should match Role enum values") {
            AccessPolicy.ALLOWED_ROLES.toList().sorted() shouldContainExactly
                Role.entries.map { it.value }.sorted()
        }

        context("hasRequiredScope") {
            test("returns true when required scope is present and allowed") {
                AccessPolicy.hasRequiredScope(
                    scopes = listOf("other.scope", Scope.BASIC_READ.value),
                    requiredScope = Scope.BASIC_READ.value,
                ) shouldBe true
            }

            test("returns false when required scope is missing") {
                AccessPolicy.hasRequiredScope(
                    scopes = listOf("other.scope"),
                    requiredScope = Scope.BASIC_READ.value,
                ) shouldBe false
            }

            test("returns false when required scope is not allowed even if present") {
                AccessPolicy.hasRequiredScope(
                    scopes = listOf("custom.read"),
                    requiredScope = "custom.read",
                ) shouldBe false
            }

            test("returns false when scopes list is empty") {
                AccessPolicy.hasRequiredScope(
                    scopes = emptyList(),
                    requiredScope = Scope.BASIC_READ.value,
                ) shouldBe false
            }
        }

        context("hasRequiredRole") {
            test("returns true when required role is present and allowed") {
                AccessPolicy.hasRequiredRole(
                    roles = listOf("other.role", Role.SKATTEKORT_READ.value),
                    requiredRole = Role.SKATTEKORT_READ.value,
                ) shouldBe true
            }

            test("returns false when required role is missing") {
                AccessPolicy.hasRequiredRole(
                    roles = listOf("other.role"),
                    requiredRole = Role.SKATTEKORT_READ.value,
                ) shouldBe false
            }

            test("returns false when required role is not allowed even if present") {
                AccessPolicy.hasRequiredRole(
                    roles = listOf("custom.role"),
                    requiredRole = "custom.role",
                ) shouldBe false
            }

            test("returns false when roles list is empty") {
                AccessPolicy.hasRequiredRole(
                    roles = emptyList(),
                    requiredRole = Role.SKATTEKORT_READ.value,
                ) shouldBe false
            }
        }
    })
