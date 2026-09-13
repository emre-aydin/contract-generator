package tech.aydin.contracts.generator.docusign;

/** Thrown when authentication with, or a request to, DocuSign fails. */
public final class DocuSignException extends RuntimeException {
    public DocuSignException(String message) {
        super(message);
    }

    public DocuSignException(String message, Throwable cause) {
        super(message, cause);
    }
}
