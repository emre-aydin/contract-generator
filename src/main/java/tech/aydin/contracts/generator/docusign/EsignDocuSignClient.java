package tech.aydin.contracts.generator.docusign;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.EnvelopeSummary;

/**
 * {@link DocuSignClient} backed by the official DocuSign eSignature SDK. Authenticates once per
 * call via {@link JwtAuthenticator}; performs real network I/O, so it is exercised only by the
 * live integration test.
 */
public final class EsignDocuSignClient implements DocuSignClient {

    private final DocuSignConfig config;
    private final JwtAuthenticator authenticator;

    public EsignDocuSignClient(DocuSignConfig config) {
        this.config = config;
        this.authenticator = new JwtAuthenticator(config);
    }

    @Override
    public String createEnvelope(EnvelopeDefinition envelope) {
        ApiClient apiClient = authenticator.authenticate();
        EnvelopesApi envelopesApi = new EnvelopesApi(apiClient);
        try {
            EnvelopeSummary summary = envelopesApi.createEnvelope(config.accountId(), envelope);
            return summary.getEnvelopeId();
        } catch (ApiException e) {
            throw new DocuSignException("Failed to create DocuSign envelope: " + e.getResponseBody(), e);
        }
    }
}
