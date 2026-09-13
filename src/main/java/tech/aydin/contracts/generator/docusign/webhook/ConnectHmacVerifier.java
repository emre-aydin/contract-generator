package tech.aydin.contracts.generator.docusign.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/**
 * Verifies the HMAC signature DocuSign Connect attaches to a webhook request when HMAC security is
 * enabled. DocuSign computes {@code Base64(HMAC-SHA256(secret, rawRequestBody))} and sends it in an
 * {@code X-DocuSign-Signature-1} header (additional keys arrive as {@code -2}, {@code -3}, ...).
 *
 * <p>This class is pure (no network) and stateless. Always verify against the <strong>raw</strong>
 * request body bytes exactly as received — re-serializing the parsed JSON will change the bytes and
 * break verification.
 */
public final class ConnectHmacVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Verifies a single signature.
     *
     * @param payload         the raw request body bytes
     * @param base64Signature the value of an {@code X-DocuSign-Signature-N} header
     * @param secret          the shared HMAC secret configured in DocuSign Connect
     * @return {@code true} if the signature matches
     */
    public boolean verify(byte[] payload, String base64Signature, String secret) {
        if (payload == null || base64Signature == null || secret == null) {
            return false;
        }
        byte[] expected = computeHmac(payload, secret);
        byte[] provided;
        try {
            provided = Base64.getDecoder().decode(base64Signature.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, provided);
    }

    /**
     * Verifies against several signatures (DocuSign may rotate/send multiple HMAC keys).
     *
     * @return {@code true} if any provided signature matches
     */
    public boolean verifyAny(byte[] payload, List<String> base64Signatures, String secret) {
        if (base64Signatures == null) {
            return false;
        }
        for (String signature : base64Signatures) {
            if (signature != null && verify(payload, signature, secret)) {
                return true;
            }
        }
        return false;
    }

    private byte[] computeHmac(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }
}
