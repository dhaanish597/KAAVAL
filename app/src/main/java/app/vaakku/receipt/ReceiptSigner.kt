package app.vaakku.receipt

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import app.vaakku.domain.receipt.Receipt
import app.vaakku.domain.receipt.SignatureBlock
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Signs a receipt's head with a key that cannot leave this phone — build plan
 * §7.2.
 *
 * ### What the signature is for, and what it is not for
 *
 * The hash chain (§7.1) proves a receipt is consistent with its own hashes.
 * Anybody can edit an event and recompute every hash after it, and the result
 * verifies cleanly — so on its own the chain proves only that a file has not
 * been *carelessly* altered. What it cannot prove is who computed those hashes.
 *
 * That is this file's job. The private key is generated inside the Android
 * Keystore and is never readable by this process or any other, so a signature
 * over `head` can only have been produced on this phone. An edited receipt can
 * be re-chained; it cannot be re-signed. `tools/packet-cli` treats a signature
 * that does not match as a failed record for exactly that reason.
 *
 * It says nothing about whether anything in the receipt is true. It is a claim
 * about a file's origin and nothing else — the packet's integrity section is
 * worded to say so, and CLAUDE.md #1 forbids it meaning any more than that.
 *
 * ### StrongBox, and being honest about it
 *
 * §7.2 asks for `setIsStrongBoxBacked(true)` with a retry without it on
 * [StrongBoxUnavailableException]. Both paths produce a usable key; they differ
 * in where it lives — a separate secure element, or the TEE on the main SoC.
 * [KeyStatus.strongBox] is then read back off the key itself through
 * `KeyInfo.getSecurityLevel()`, rather than inferred from which attempt did not
 * throw. CLAUDE.md #8: the packet prints this as "reported by the phone",
 * because even a true reading here cannot be *proved* to a third party without
 * attestation against a manufacturer root, and neither this app nor an offline
 * CLI carries one.
 */
object ReceiptSigner {

    /** §7.2 fixes the alias. */
    const val ALIAS = "vaakku_receipt"

    private const val PROVIDER = "AndroidKeyStore"
    private const val CURVE = "secp256r1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /**
     * Where the key ended up, and whether it is in a separate secure element.
     *
     * [strongBox] is the outcome, not the intent — see the class comment.
     */
    data class KeyStatus(val strongBox: Boolean)

    /**
     * The signature block for [receipt], or null when this phone could not
     * produce one.
     *
     * Null is a real outcome and not an error to shout about. A receipt without
     * a signature still verifies, still renders, and still says in plain words
     * that it is unsigned — `tools/packet-cli` distinguishes "not signed" from
     * "signed and the signature does not match", and only the second is a
     * failure. Refusing to export a receipt because the keystore would not
     * cooperate would take away the record itself, which is the one thing the
     * buyer definitely needs.
     *
     * [attestationChallenge] is `h0`'s bytes (§7.2). It is only consulted when
     * the key is generated; an existing key keeps the challenge it was born
     * with.
     */
    fun sign(receipt: Receipt, attestationChallenge: ByteArray): SignatureBlock? {
        val store = try {
            KeyStore.getInstance(PROVIDER).apply { load(null) }
        } catch (error: Exception) {
            return null
        }

        val status = ensureKey(store, attestationChallenge) ?: return null

        return try {
            val key = store.getKey(ALIAS, null) as? PrivateKey ?: return null
            val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
            signer.initSign(key)
            // The head's 64 ASCII hex characters, not the 32 bytes they spell.
            // §7.2 says "sign head", and head is a string in this format;
            // signing the text is the reading that needs no further convention,
            // and it is what a verifier can reproduce from the file without
            // re-deriving anything. tools/packet-cli/lib/verify.js updates with
            // `'ascii'` against the same string. A phone that signed the decoded
            // bytes instead would produce a signature that verifier reports as
            // broken.
            signer.update(receipt.head.toByteArray(Charsets.US_ASCII))
            val signature = Base64.encodeToString(signer.sign(), Base64.NO_WRAP)

            val chain = store.getCertificateChain(ALIAS)
                ?.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }
                ?: return null
            if (chain.isEmpty()) return null

            SignatureBlock(signature = signature, certChain = chain, strongBox = status.strongBox)
        } catch (error: Exception) {
            // A key that exists but will not sign — revoked because the user
            // changed their lock screen, or a keystore that has gone away with
            // a system update. Unsigned is the honest answer; a stack trace at
            // export time is not an improvement on it.
            null
        }
    }

    /**
     * Returns the status of the existing key, generating one if there is none.
     *
     * §7.2's order: StrongBox first, then TEE on [StrongBoxUnavailableException].
     */
    private fun ensureKey(store: KeyStore, attestationChallenge: ByteArray): KeyStatus? {
        if (store.containsAlias(ALIAS)) return readStatus(store)

        if (generate(strongBox = true, attestationChallenge = attestationChallenge)) {
            return readStatus(store)
        }
        if (generate(strongBox = false, attestationChallenge = attestationChallenge)) {
            return readStatus(store)
        }
        return null
    }

    /** True when a key now exists under [ALIAS]. */
    private fun generate(strongBox: Boolean, attestationChallenge: ByteArray): Boolean = try {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            // §7.2. The challenge is h0, which ties the certificate to this
            // session's opening hash: an attestation produced for one session
            // cannot be presented as another's.
            .setAttestationChallenge(attestationChallenge)
            // Deliberately NOT setUserAuthenticationRequired(true). A receipt is
            // written when the session ends, and requiring a fingerprint at that
            // moment would mean a buyer who walks away from the desk loses the
            // record of the conversation they just had. The key protects the
            // file's origin, not access to the phone.
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
            .apply { initialize(spec) }
            .generateKeyPair()
        true
    } catch (error: StrongBoxUnavailableException) {
        // The documented signal that this phone has no secure element. Not a
        // problem — the caller retries in the TEE.
        false
    } catch (error: Exception) {
        // Some devices decline a StrongBox key with something other than the
        // documented exception, and at least one family throws on
        // `setAttestationChallenge` when the key would be attested by a
        // provisioning service that is unreachable — which, on an offline phone,
        // it always is. Returning false lets the TEE attempt run; if that fails
        // too, `sign` answers null and the receipt is unsigned rather than
        // absent.
        false
    }

    /**
     * Asks the key where it actually lives.
     *
     * This is read back through `KeyInfo.getSecurityLevel()` rather than
     * inferred from which generation attempt did not throw, and it is the
     * difference between a measurement and a guess. Two cases make the
     * difference concrete: a key generated in an earlier session, where there is
     * no attempt to remember; and a device that accepts
     * `setIsStrongBoxBacked(true)` without honouring it, where "no exception"
     * would have printed StrongBox for a key in the TEE. CLAUDE.md #8 does not
     * allow the second.
     *
     * `SECURITY_LEVEL_STRONGBOX` is the only value reported as StrongBox.
     * `UNKNOWN_SECURE` documents itself as "at least equivalent to
     * TRUSTED_ENVIRONMENT", which is not the same as being in a separate secure
     * element, so it reads as TEE. Anything unreadable also reads as TEE: the
     * receipt is still signed, and the claim about hardware is the one thing
     * that must not be overstated.
     */
    private fun readStatus(store: KeyStore): KeyStatus? {
        val key = try {
            store.getKey(ALIAS, null) as? PrivateKey ?: return null
        } catch (error: Exception) {
            return null
        }
        val level = try {
            val factory = KeyFactory.getInstance(key.algorithm, PROVIDER)
            factory.getKeySpec(key, KeyInfo::class.java).securityLevel
        } catch (error: Exception) {
            KeyProperties.SECURITY_LEVEL_UNKNOWN
        }
        return KeyStatus(strongBox = level == KeyProperties.SECURITY_LEVEL_STRONGBOX)
    }
}
