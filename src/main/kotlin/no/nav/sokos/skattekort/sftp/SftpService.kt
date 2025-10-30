package no.nav.sokos.skattekort.sftp

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.SftpConfig

private val logger = KotlinLogging.logger {}

private const val ARENA_FILNAVN_AARSKJOERING = "GR60_AAR_{yyyy}.dat"
private const val ARENA_FILNAVN_DAGSKJOERING = "GR60_DAG_{yyyyMMdd}.dat"

class SftpService(
    private val sftpConfig: SftpConfig,
) {
    fun uploadFile(
        fileName: String,
        content: String,
        directory: Directories = Directories.OUTBOUND,
    ) {
        sftpConfig.channel { connector ->
            val path = "${directory.value}/$fileName"
            runCatching {
                connector.put(content.toByteArray().inputStream(), path)
                logger.debug { "$fileName ble opprettet i mappen $path" }
            }.onFailure { exception ->
                logger.error(exception) { "$fileName ble ikke opprettet i mappen $path." }
                throw exception
            }
        }
    }

    fun isSftpConnectionEnabled(): Boolean =
        runCatching {
            sftpConfig.channel { connector ->
                // ChannelSftp from JSch assumed
                val session = connector.session
                require(session.isConnected) { "Session not connected" }
                connector.stat(".") // noop: check current directory metadata
            }
        }.isSuccess

    fun createArenaFilename(aarligBestilling: Boolean = false): String {
        val currentDate = LocalDate.now()

        return when {
            aarligBestilling -> ARENA_FILNAVN_AARSKJOERING.replace("{yyyy}", currentDate.plusYears(1).format(DateTimeFormatter.ofPattern("yyyy")))
            else -> ARENA_FILNAVN_DAGSKJOERING.replace("{yyyyMMdd}", currentDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
        }
    }
}

enum class Directories(
    var value: String,
) {
    OUTBOUND("/outbound/GR60"),
}
