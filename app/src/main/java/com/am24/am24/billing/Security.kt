package com.am24.am24.billing

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object Security {
    const val PLAY_BILLING_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvfr1B3hiyp0XA1KIDmU6iTSaxxfbeHXMy5HhHcJf0CqfvheBVWsXsNqqLSBWHb7y6Le1DOXiZ3UCDyAHiLsthjdyRmac0SMPu8NGbpgcchbYKCdfK9LJJhkmKPBC/qWcjnKQAH1OGetrZBLXX7E4wHFAWxpAlcSI0PMM0Wj9c+BVHz9DI9Kqv+rLpuKMRZFoTNUuDcmfsomua6z8uRttyiVsqbW0NVEFkzUP+GC5UL3vs+f5jPfnbMAK9alyTNM+Qm/c5uZ2rWZClrKvPNRX6veh/5jhR2QO92ed41kWEbXJAYvgvXaqOnnvJvHeTWSZ5q0I7af5/CRmX8oHJaSlPwIDAQAB"

    fun verifyPurchase(base64PublicKey: String, signedData: String, signature: String?): Boolean {
        if (signedData.isBlank() || signature.isNullOrBlank()) {
            return false
        }
        return try {
            val key = generatePublicKey(base64PublicKey)
            verify(key, signedData, signature)
        } catch (e: Exception) {
            false
        }
    }

    private fun generatePublicKey(encodedPublicKey: String): PublicKey {
        val decodedKey = Base64.decode(encodedPublicKey, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(X509EncodedKeySpec(decodedKey))
    }

    private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initVerify(publicKey)
        sig.update(signedData.toByteArray())
        val decodedSignature = Base64.decode(signature, Base64.DEFAULT)
        return sig.verify(decodedSignature)
    }
}