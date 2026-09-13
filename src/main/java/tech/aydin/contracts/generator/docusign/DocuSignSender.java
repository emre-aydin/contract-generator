package tech.aydin.contracts.generator.docusign;

/**
 * Public entry point for sending a generated contract PDF to DocuSign for signature.
 *
 * <pre>{@code
 * DocuSignConfig config = new DocuSignConfig(accountId, "account-d.docusign.com",
 *         integrationKey, userId, privateKeyBytes);
 * DocuSignSender sender = DocuSignSender.forLiveDocuSign(config);
 * String envelopeId = sender.sendForSignature(new SendRequest(
 *         "Consulting Agreement", pdfBytes, "Please sign: Consulting Agreement",
 *         List.of(new Signer("Jane Doe", "jane@example.com"))));
 * }</pre>
 */
public final class DocuSignSender {

    private final EnvelopeFactory envelopeFactory;
    private final DocuSignClient client;

    public DocuSignSender(EnvelopeFactory envelopeFactory, DocuSignClient client) {
        this.envelopeFactory = envelopeFactory;
        this.client = client;
    }

    /** Convenience factory wiring up the real SDK-backed client for the given config. */
    public static DocuSignSender forLiveDocuSign(DocuSignConfig config) {
        return new DocuSignSender(new EnvelopeFactory(), new EsignDocuSignClient(config));
    }

    /**
     * Builds an envelope from the request and sends it to DocuSign.
     *
     * @return the DocuSign envelope id
     */
    public String sendForSignature(SendRequest request) {
        return client.createEnvelope(envelopeFactory.build(request));
    }
}
