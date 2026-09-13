package tech.aydin.contracts.generator.docusign.webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of parsing a DocuSign Connect notification: envelope metadata plus the documents it
 * carried (each with its parsed AcroForm field values).
 *
 * <p>Documents are only present when the Connect configuration has "Include Documents" enabled and
 * the envelope has reached a state where DocuSign attaches the signed PDF (typically
 * {@code completed}). Otherwise {@link #documents()} is empty; {@link #status()} still reflects the
 * envelope state so the caller can decide what to do.
 *
 * @param envelopeId the DocuSign envelope id
 * @param status     the envelope status (e.g. {@code "completed"}, {@code "sent"})
 * @param documents  the documents carried by the notification, in payload order
 */
public record SignedEnvelope(String envelopeId, String status, List<SignedDocument> documents) {

    public SignedEnvelope {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /** Whether the envelope status is {@code completed} (case-insensitive). */
    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }

    /**
     * All documents' field values merged into a single map, in document order. When two documents
     * declare the same field name, the later document wins. In the common single-content-document
     * case this simply returns that document's fields.
     */
    public Map<String, String> fields() {
        Map<String, String> merged = new LinkedHashMap<>();
        for (SignedDocument document : documents) {
            merged.putAll(document.fields());
        }
        return merged;
    }
}
