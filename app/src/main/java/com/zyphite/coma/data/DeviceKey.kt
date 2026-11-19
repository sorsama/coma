package com.zyphite.coma.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * This phone's identity towards a relay: a P-256 key pair whose private half is made inside the
 * Android Keystore and cannot be taken out. A relay learns the public half when the phone pairs and
 * then accepts only requests signed by the private one, so a pairing cannot be copied to another phone.
 */
object DeviceKey {
    private const val ALIAS = "coma-device"

    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensure() {
        if (store().containsAlias(ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
    }

    /** The public key as base64 SPKI, the form a relay stores. */
    fun public(): String {
        ensure()
        return Base64.encodeToString(store().getCertificate(ALIAS).publicKey.encoded, Base64.NO_WRAP)
    }

    /** An ECDSA signature over [text], base64. */
    fun sign(text: String): String {
        ensure()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(store().getKey(ALIAS, null) as PrivateKey)
        signature.update(text.toByteArray())
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }
}
