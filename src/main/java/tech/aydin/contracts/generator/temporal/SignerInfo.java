package tech.aydin.contracts.generator.temporal;

/**
 * A signer for the generated contract, passed as part of a {@link ContractSigningRequest}.
 *
 * @param name         the signer's full name
 * @param email        the signer's email address
 * @param routingOrder 1-based routing order (use 1 for a single signer)
 */
public record SignerInfo(String name, String email, int routingOrder) {

    /** Creates a signer with the default routing order of 1. */
    public SignerInfo(String name, String email) {
        this(name, email, 1);
    }
}
