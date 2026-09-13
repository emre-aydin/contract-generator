package tech.aydin.contracts.generator.docusign;

import com.docusign.esign.model.EnvelopeDefinition;

/**
 * Sends a built {@link EnvelopeDefinition} to DocuSign. Implementations perform network I/O;
 * this seam exists so {@link DocuSignSender} can be unit-tested with a fake.
 */
public interface DocuSignClient {

    /**
     * Creates (and, since the envelope status is {@code sent}, immediately sends) the envelope.
     *
     * @return the DocuSign envelope id
     */
    String createEnvelope(EnvelopeDefinition envelope);
}
