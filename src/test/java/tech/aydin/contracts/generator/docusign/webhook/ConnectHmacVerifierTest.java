package tech.aydin.contracts.generator.docusign.webhook;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectHmacVerifierTest {

    private final ConnectHmacVerifier verifier = new ConnectHmacVerifier();

    // Precomputed with: Base64(HMAC-SHA256(key="topsecret", body="{\"hello\":\"world\"}"))
    private static final byte[] BODY = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
    private static final String SECRET = "topsecret";
    private static final String VALID_SIGNATURE = "r9AGF8649j5l6lwxDwa/eMOQHnpxPbUy4l2iatY8cjY=";

    @Test
    void acceptsValidSignature() {
        assertTrue(verifier.verify(BODY, VALID_SIGNATURE, SECRET));
    }

    @Test
    void rejectsWrongSecret() {
        assertFalse(verifier.verify(BODY, VALID_SIGNATURE, "not-the-secret"));
    }

    @Test
    void rejectsTamperedBody() {
        byte[] tampered = "{\"hello\":\"WORLD\"}".getBytes(StandardCharsets.UTF_8);
        assertFalse(verifier.verify(tampered, VALID_SIGNATURE, SECRET));
    }

    @Test
    void rejectsMalformedBase64() {
        assertFalse(verifier.verify(BODY, "not base64 !!!", SECRET));
    }

    @Test
    void rejectsNulls() {
        assertFalse(verifier.verify(null, VALID_SIGNATURE, SECRET));
        assertFalse(verifier.verify(BODY, null, SECRET));
        assertFalse(verifier.verify(BODY, VALID_SIGNATURE, null));
    }

    @Test
    void verifyAnyMatchesOneOfSeveral() {
        List<String> signatures = List.of("AAAA", VALID_SIGNATURE, "BBBB");
        assertTrue(verifier.verifyAny(BODY, signatures, SECRET));
    }

    @Test
    void verifyAnyFailsWhenNoneMatch() {
        List<String> signatures = List.of("AAAA", "BBBB");
        assertFalse(verifier.verifyAny(BODY, signatures, SECRET));
    }
}
