package no.nav.sokos.skattekort

import java.nio.file.Paths

import kotlin.io.path.createDirectories

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.ApplicationInfrastructureListener.dbContainer

class DbDumper :
    FunSpec({
        extension(ApplicationInfrastructureListener)

        test("dump databaseskjema") {
            dbContainer().execInContainer(
                "/usr/bin/pg_dump",
                "--username=${dbContainer().username}",
                "--schema-only",
                "${dbContainer().databaseName}",
                "--file=/tmp/pg_dump.sql",
            )
            Paths.get("build/dbschema").createDirectories()
            dbContainer().copyFileFromContainer("/tmp/pg_dump.sql", "build/dbschema/pg_dump.sql")
        }
    })
