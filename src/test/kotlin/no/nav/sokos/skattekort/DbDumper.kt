package no.nav.sokos.skattekort

import java.nio.file.Paths

import kotlin.io.path.createDirectories

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.infrastructure.DbListener

class DbDumper :
    FunSpec({
        extension(DbListener)

        test("dump databaseskjema") {
            DbListener.container.execInContainer(
                "/usr/bin/pg_dump",
                "--username=${DbListener.container.username}",
                "--schema-only",
                "${DbListener.container.databaseName}",
                "--file=/tmp/pg_dump.sql",
            )
            Paths.get("build/dbschema").createDirectories()
            DbListener.container.copyFileFromContainer("/tmp/pg_dump.sql", "build/dbschema/pg_dump.sql")
        }
    })
