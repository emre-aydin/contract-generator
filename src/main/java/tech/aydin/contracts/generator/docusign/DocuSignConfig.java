package tech.aydin.contracts.generator.docusign;

/**
 * DocuSign JWT Grant + REST connection settings.
 *
 * <p>{@code basePath} is the account-specific REST API base URL (e.g.
 * {@code https://demo.docusign.net/restapi} for the demo environment, obtained after
 * authenticating via {@link JwtAuthenticator#authenticate()}). {@code oAuthBasePath} is the
 * OAuth authorization server host, without scheme (e.g. {@code account-d.docusign.com} for demo,
 * {@code account.docusign.com} for production).
 *
 * @param accountId     the DocuSign account id (GUID) to send envelopes under
 * @param oAuthBasePath OAuth host, e.g. {@code account-d.docusign.com}
 * @param integrationKey the integration key (OAuth client id) of the JWT app
 * @param userId        the GUID of the user being impersonated
 * @param privateKey    the RSA private key (PEM-encoded bytes) registered for the integration key
 */
public record DocuSignConfig(
        String accountId,
        String oAuthBasePath,
        String integrationKey,
        String userId,
        byte[] privateKey) {

    public DocuSignConfig {
        require(accountId, "accountId");
        require(oAuthBasePath, "oAuthBasePath");
        require(integrationKey, "integrationKey");
        require(userId, "userId");
        if (privateKey == null || privateKey.length == 0) {
            throw new IllegalArgumentException("privateKey must not be empty");
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
