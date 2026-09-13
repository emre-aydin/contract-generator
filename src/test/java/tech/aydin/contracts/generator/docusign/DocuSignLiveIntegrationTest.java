package tech.aydin.contracts.generator.docusign;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tech.aydin.contracts.generator.ContractGenerator;
import tech.aydin.contracts.generator.model.ContractData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sends a real envelope to DocuSign's demo environment using JWT auth. Skipped unless
 * {@code DOCUSIGN_LIVE_TEST=true} and the other required env vars are set, so it never runs in
 * ordinary CI/unit test runs.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code DOCUSIGN_LIVE_TEST} = {@code true}</li>
 *   <li>{@code DOCUSIGN_ACCOUNT_ID} — the DocuSign account id (GUID)</li>
 *   <li>{@code DOCUSIGN_INTEGRATION_KEY} — the JWT integration key (OAuth client id)</li>
 *   <li>{@code DOCUSIGN_USER_ID} — the GUID of the impersonated user (must have granted consent)</li>
 *   <li>{@code DOCUSIGN_PRIVATE_KEY_PATH} — path to the RSA private key PEM file registered for
 *       the integration key</li>
 *   <li>{@code DOCUSIGN_SIGNER_EMAIL}, {@code DOCUSIGN_SIGNER_NAME} — recipient of the test envelope</li>
 * </ul>
 * Optional: {@code DOCUSIGN_OAUTH_BASE_PATH} (defaults to {@code account-d.docusign.com}, the
 * demo environment).
 */
@EnabledIfEnvironmentVariable(named = "DOCUSIGN_LIVE_TEST", matches = "true")
class DocuSignLiveIntegrationTest {

    @Test
    void sendsGeneratedContractForSignature() throws Exception {
        DocuSignConfig config = new DocuSignConfig(
                env("DOCUSIGN_ACCOUNT_ID"),
                System.getenv().getOrDefault("DOCUSIGN_OAUTH_BASE_PATH", "account-d.docusign.com"),
                env("DOCUSIGN_INTEGRATION_KEY"),
                env("DOCUSIGN_USER_ID"),
                Files.readAllBytes(Path.of(env("DOCUSIGN_PRIVATE_KEY_PATH"))));

        byte[] pdf = new ContractGenerator().generate(
                "contract",
                ContractData.builder()
                        .put("title", "Consulting Services Agreement")
                        .put("provider", "Acme Consulting LLC")
                        .put("client", "Globex Corporation")
                        .put("effectiveDate", "2026-09-13")
                        .put("recital", "The Provider agrees to deliver consulting services to the Client.")
                        .build(),
                java.util.Set.of("party.name", "party.email", "party.notes",
                        "agree.terms", "sig.name", "sig.title", "sig.date"),
                java.util.Set.of("party.name", "party.email", "sig.name", "sig.date"));

        SendRequest request = new SendRequest(
                "Consulting Services Agreement",
                pdf,
                "Please sign: Consulting Services Agreement (contract-generator live test)",
                List.of(new Signer(env("DOCUSIGN_SIGNER_NAME"), env("DOCUSIGN_SIGNER_EMAIL"))));

        String envelopeId = DocuSignSender.forLiveDocuSign(config).sendForSignature(request);

        assertNotNull(envelopeId);
        assertFalse(envelopeId.isBlank());
        System.out.println("Created DocuSign envelope: " + envelopeId);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}
