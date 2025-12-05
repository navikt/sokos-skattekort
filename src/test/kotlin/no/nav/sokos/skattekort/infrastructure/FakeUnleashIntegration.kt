package no.nav.sokos.skattekort.infrastructure

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.PropertiesConfig.Environment

/**
 For enkel unit-testing
 */
class FakeUnleashIntegration :
    UnleashIntegration(
        PropertiesConfig.UnleashProperties("", "", ""),
        PropertiesConfig.ApplicationProperties(
            "foo",
            Environment.TEST,
            false,
            "",
            "",
            "",
        ),
    )
