package no.nav.sokos.lavendel

import java.nio.file.Paths

import kotlin.io.path.createDirectories

class DbDumper :
    EndToEndFunSpec({ dbContainer, jmsTestServer ->

        test("dump databaseskjema") {
            dbContainer.execInContainer(
                "/usr/bin/pg_dump",
                "--username=${dbContainer.username}",
                "--schema-only",
                "${dbContainer.databaseName}",
                "--file=/tmp/pg_dump.sql",
            )
            Paths.get("build/dbschema").createDirectories()
            dbContainer.copyFileFromContainer("/tmp/pg_dump.sql", "build/dbschema/pg_dump.sql")
        }
    })
