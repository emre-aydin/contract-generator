package tech.aydin.contracts.generator.docusign;

import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.client.auth.OAuth;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates against DocuSign using the JWT Grant flow (service integration; no browser
 * interaction). Performs network I/O, so it is exercised only by the live integration test.
 *
 * <p>Before first use, the impersonated user must have granted consent to the integration key
 * once (via the DocuSign "Grant Consent" URL); this class does not perform the consent step.
 */
public final class JwtAuthenticator {

    private static final List<String> SCOPES = List.of("signature", "impersonation");
    private static final long JWT_LIFETIME_SECONDS = 3600;

    private final DocuSignConfig config;

    public JwtAuthenticator(DocuSignConfig config) {
        this.config = config;
    }

    /**
     * Requests a JWT user token and returns an {@link ApiClient} configured with the resulting
     * access token and the {@code accountId}'s base REST path (looked up via the userinfo
     * endpoint, so it works correctly in both the demo and production environments).
     */
    public ApiClient authenticate() {
        ApiClient apiClient = new ApiClient();
        apiClient.setOAuthBasePath(config.oAuthBasePath());
        try {
            OAuth.OAuthToken token = apiClient.requestJWTUserToken(
                    config.integrationKey(),
                    config.userId(),
                    SCOPES,
                    config.privateKey(),
                    JWT_LIFETIME_SECONDS);
            apiClient.setAccessToken(token.getAccessToken(), token.getExpiresIn());

            OAuth.UserInfo userInfo = apiClient.getUserInfo(token.getAccessToken());
            String baseUri = userInfo.getAccounts().stream()
                    .filter(account -> account.getAccountId().equals(config.accountId()))
                    .findFirst()
                    .map(OAuth.Account::getBaseUri)
                    .orElseThrow(() -> new DocuSignException(
                            "Authenticated user has no access to accountId " + config.accountId()));
            apiClient.setBasePath(baseUri + "/restapi");
            return apiClient;
        } catch (ApiException | IOException e) {
            throw new DocuSignException("Failed to obtain DocuSign JWT user token", e);
        }
    }
}
