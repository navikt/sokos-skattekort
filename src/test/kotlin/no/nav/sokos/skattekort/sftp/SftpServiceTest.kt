package no.nav.sokos.skattekort.sftp

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.listener.SftpListener

class SftpServiceTest :
    FunSpec({
        extensions(SftpListener)
    })
