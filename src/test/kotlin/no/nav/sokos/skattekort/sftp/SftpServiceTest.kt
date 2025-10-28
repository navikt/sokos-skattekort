package no.nav.sokos.skattekort.sftp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.assertThrows

import no.nav.sokos.skattekort.config.SftpConfig
import no.nav.sokos.skattekort.listener.SftpListener

class SftpServiceTest :
    FunSpec({
        extensions(SftpListener)

        val sftpService: SftpService by lazy {
            SftpService(SftpConfig(SftpListener.sftpProperties))
        }

        test("uploadFile laster opp fil til SFTP server") {
            val fileName = "test.txt"
            val content = "hello world"

            sftpService.uploadFile(fileName, Directories.OUTBOUND, content)

            val downloaded = SftpListener.downloadFile(fileName, Directories.OUTBOUND)
            downloaded shouldBe content
        }

        test("uploadFile throws exception og logs på feil") {
            val sftpService = mockk<SftpService>()

            every { sftpService.uploadFile(any(), any(), any()) } throws RuntimeException("feil")

            val exception =
                assertThrows<RuntimeException> {
                    sftpService.uploadFile("file.txt", Directories.OUTBOUND, "content")
                }
            exception.message shouldContain "feil"
        }

        test("isSftpConnectionEnabled returnere true når SFTP er oppe") {
            sftpService.isSftpConnectionEnabled() shouldBe true
        }
    })
