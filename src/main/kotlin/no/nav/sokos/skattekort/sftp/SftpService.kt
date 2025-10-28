package no.nav.sokos.skattekort.sftp

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.SftpConfig

private val logger = KotlinLogging.logger {}

class SftpService(
    private val sftpConfig: SftpConfig,
) {
    fun uploadFile(
        fileName: String,
        directory: Directories,
        content: String,
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
}

enum class Directories(
    var value: String,
) {
    OUTBOUND("/outbound"),
}
