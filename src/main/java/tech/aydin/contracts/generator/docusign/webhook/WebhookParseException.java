package tech.aydin.contracts.generator.docusign.webhook;

/** Thrown when a DocuSign Connect webhook payload is malformed or cannot be parsed. */
public final class WebhookParseException extends RuntimeException {
    public WebhookParseException(String message) {
        super(message);
    }

    public WebhookParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
