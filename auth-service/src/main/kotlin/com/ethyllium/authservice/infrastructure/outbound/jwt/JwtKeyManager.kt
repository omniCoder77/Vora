package com.ethyllium.authservice.infrastructure.outbound.jwt

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Component
class JwtKeyManager(
    @Value($$"${jwt.rsa-key-pair-path}") private val keyPairPath: String,
) {

    private val keyFactory = KeyFactory.getInstance("RSA")
    private val keysDir = keyPairPath

    val privateKey: RSAPrivateKey by lazy { loadPrivateKey() }
    val publicKey: RSAPublicKey by lazy { loadPublicKey() }

    private fun loadPrivateKey(): RSAPrivateKey {
        val pemContent = File("$keysDir/private.pem").readText()
        val cleanKey = removePemHeaders(pemContent, "PRIVATE KEY")

        val decoded = Base64.getDecoder().decode(cleanKey)
        val keySpec = PKCS8EncodedKeySpec(decoded)

        return keyFactory.generatePrivate(keySpec) as RSAPrivateKey
    }

    private fun loadPublicKey(): RSAPublicKey {
        val pemContent = File("$keysDir/public.pem").readText()
        val cleanKey = removePemHeaders(pemContent, "PUBLIC KEY")

        val decoded = Base64.getDecoder().decode(cleanKey)
        val keySpec = X509EncodedKeySpec(decoded)

        return keyFactory.generatePublic(keySpec) as RSAPublicKey
    }

    private fun removePemHeaders(content: String, type: String): String {
        return content
            .replace("-----BEGIN $type-----", "")
            .replace("-----END $type-----", "")
            .replace("\\s".toRegex(), "")
    }
}