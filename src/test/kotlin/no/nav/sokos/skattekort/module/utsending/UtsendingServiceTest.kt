package no.nav.sokos.skattekort.module.utsending

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.SftpListener

class UtsendingServiceTest :
    FunSpec({
        extensions(DbListener, SftpListener)

        test("handleArenaUtsending skal laste opp fil til SFTP") {
            // Test implementation goes here
        }
    })
