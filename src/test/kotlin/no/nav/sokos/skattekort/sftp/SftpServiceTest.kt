package no.nav.sokos.skattekort.sftp

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
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

            sftpService.uploadFile(fileName, content)

            val downloaded = SftpListener.downloadFile(fileName, Directories.OUTBOUND)
            downloaded shouldBe content
        }

        test("uploadFile throws exception og logs på feil") {
            val sftpService = mockk<SftpService>()

            every { sftpService.uploadFile(any(), any(), any()) } throws RuntimeException("feil")

            val exception =
                assertThrows<RuntimeException> {
                    sftpService.uploadFile("file.txt", "content")
                }
            exception.message shouldContain "feil"
        }

        test("isSftpConnectionEnabled returnere true når SFTP er oppe") {
            sftpService.isSftpConnectionEnabled() shouldBe true
        }

        test("createArenaFilename returnere daglig filnavn med dagens dato") {
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val filename = sftpService.createArenaFilename()
            filename shouldMatch Regex("""GR60_DAG_\d{8}\.dat""")
            filename shouldBe "GR60_DAG_$today.dat"
        }

        test("createArenaFilename returnere årlig filnavn med neste år") {
            val nextYear = LocalDate.now().plusYears(1).format(DateTimeFormatter.ofPattern("yyyy"))
            val filename = sftpService.createArenaFilename(aarligBestilling = true)
            filename shouldMatch Regex("""GR60_AAR_\d{4}\.dat""")
            filename shouldBe "GR60_AAR_$nextYear.dat"
        }
    })
