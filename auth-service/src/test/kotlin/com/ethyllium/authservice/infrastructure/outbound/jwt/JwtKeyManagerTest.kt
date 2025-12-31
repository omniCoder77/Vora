package com.ethyllium.authservice.infrastructure.outbound.jwt

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.*

class JwtKeyManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var jwtKeyManager: JwtKeyManager

    @BeforeEach
    fun setup() {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()

        val privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val privatePem = """
            -----BEGIN PRIVATE KEY-----
            $privateKeyBase64
            -----END PRIVATE KEY-----
        """.trimIndent()
        File(tempDir.toFile(), "private.pem").writeText(privatePem)

        val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val publicPem = """
            -----BEGIN PUBLIC KEY-----
            $publicKeyBase64
            -----END PUBLIC KEY-----
        """.trimIndent()
        File(tempDir.toFile(), "public.pem").writeText(publicPem)

        jwtKeyManager = JwtKeyManager(tempDir.toAbsolutePath().toString())
    }

    @Test
    fun `should load private key successfully`() {
        val privateKey = jwtKeyManager.privateKey

        assertNotNull(privateKey)
        assertNotNull(privateKey.modulus)
        assertNotNull(privateKey.privateExponent)
    }

    @Test
    fun `should load public key successfully`() {
        val publicKey = jwtKeyManager.publicKey

        assertNotNull(publicKey)
        assertNotNull(publicKey.modulus)
        assertNotNull(publicKey.publicExponent)
    }

    @Test
    fun `keys should be a valid RSA pair`() {
        val privateKey = jwtKeyManager.privateKey
        val publicKey = jwtKeyManager.publicKey

        assertTrue(privateKey.modulus == publicKey.modulus)
    }
}