package no.nav.sokos.skattekort.infrastructure

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import mu.KotlinLogging
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.shaded.org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.testcontainers.shaded.org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.testcontainers.shaded.org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.testcontainers.shaded.org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.testcontainers.shaded.org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.testcontainers.shaded.org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.testcontainers.shaded.org.bouncycastle.util.io.pem.PemObject
import org.testcontainers.shaded.org.bouncycastle.util.io.pem.PemWriter

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.SftpConfig
import no.nav.sokos.skattekort.sftp.Directories

private val logger = KotlinLogging.logger {}

object SftpListener : TestListener {
    private val keyPair = generateKeyPair()
    private val privateKeyFile = createPrivateKeyFile(keyPair.private)
    private val sftpContainer = setupSftpTestContainer(keyPair.public)

    val sftpProperties =
        PropertiesConfig.SftpProperties(
            host = "localhost",
            user = "foo",
            privateKey = privateKeyFile.absolutePath,
            keyPassword = "pass",
            port = 5678,
        )

    override suspend fun beforeSpec(spec: Spec) {
        if (!sftpContainer.isRunning) {
            sftpContainer.start()
        }
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        clearDirectory(Directories.OUTBOUND)
    }

    fun clearDirectory(directory: Directories) {
        val sftpConfig = SftpConfig(sftpProperties)
        sftpConfig.channel { channelSftp ->
            val files = channelSftp.ls(directory.value).filter { !it.attrs.isDir }.map { it.filename }
            files.forEach { file ->
                channelSftp.rm("${directory.value}/$file")
            }
        }
    }

    fun downloadFile(
        fileName: String,
        directory: Directories,
    ): String {
        val sftpConfig = SftpConfig(sftpProperties)

        return sftpConfig.channel { channelSftp ->
            val inputStream = channelSftp.get("${directory.value}/$fileName")
            inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun setupSftpTestContainer(publicKey: AsymmetricKeyParameter): GenericContainer<*> {
        val publicKeyAsBytes = convertToByteArray(publicKey)
        return GenericContainer("atmoz/sftp:alpine")
            .withCopyToContainer(
                Transferable.of(publicKeyAsBytes),
                "/home/foo/.ssh/keys/id_rsa.pub",
            ).withExposedPorts(22)
            .withCreateContainerCmdModifier { cmd -> cmd.hostConfig!!.withPortBindings(PortBinding(Ports.Binding.bindPort(5678), ExposedPort(22))) }
            .withCommand("foo::::${Directories.OUTBOUND.value}")
    }

    private fun createPrivateKeyFile(privateKey: AsymmetricKeyParameter): File {
        val privateKeyString = convertToString(privateKey)
        return File("src/test/resources/privateKey").apply {
            writeText(privateKeyString)
        }
    }

    private fun generateKeyPair(): AsymmetricCipherKeyPair {
        val keyPairGenerator = Ed25519KeyPairGenerator()
        keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        return keyPairGenerator.generateKeyPair()
    }

    private fun convertToString(privateKey: AsymmetricKeyParameter): String {
        val outputStream = ByteArrayOutputStream()
        PemWriter(OutputStreamWriter(outputStream)).use { writer ->
            val encodedPrivateKey =
                OpenSSHPrivateKeyUtil.encodePrivateKey(privateKey)
            writer.writeObject(
                PemObject(
                    "OPENSSH PRIVATE KEY",
                    encodedPrivateKey,
                ),
            )
        }
        return outputStream.toString()
    }

    private fun convertToByteArray(publicKey: AsymmetricKeyParameter): ByteArray {
        val openSshEncodedPublicKey = OpenSSHPublicKeyUtil.encodePublicKey(publicKey)
        val base64EncodedPublicKey = Base64.getEncoder().encodeToString(openSshEncodedPublicKey)
        return "ssh-ed25519 $base64EncodedPublicKey".toByteArray(StandardCharsets.UTF_8)
    }
}
