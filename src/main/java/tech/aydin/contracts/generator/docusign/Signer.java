package tech.aydin.contracts.generator.docusign;

/**
 * A single envelope signer.
 *
 * <p>With DocuSign's tab auto-detection ({@code transformPdfFields=true}), all converted tabs
 * are assigned to the first signer (lowest {@code routingOrder}, then declaration order) unless
 * field names encode the intended recipient. For multi-signer envelopes, either route fields
 * across templates/documents per signer, or accept that all fields land on the first signer.
 *
 * @param name         the signer's full name
 * @param email        the signer's email address
 * @param routingOrder 1-based routing order; signers with the same order sign in parallel
 */
public record Signer(String name, String email, int routingOrder) {

    public Signer {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (routingOrder < 1) {
            throw new IllegalArgumentException("routingOrder must be >= 1");
        }
    }

    /** Creates a signer with the default routing order of 1. */
    public Signer(String name, String email) {
        this(name, email, 1);
    }
}
