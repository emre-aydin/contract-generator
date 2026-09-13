package tech.aydin.contracts.generator.docusign.webhook;

import java.util.Map;

/**
 * A single document extracted from a DocuSign Connect notification, together with its parsed
 * AcroForm field values.
 *
 * @param documentId the DocuSign document id (e.g. {@code "1"}, or {@code "certificate"} for the
 *                   completion certificate / summary document)
 * @param name       the document's display name
 * @param type       the DocuSign document type (e.g. {@code "content"}, {@code "summary"})
 * @param pdfBytes   the decoded PDF bytes
 * @param fields     fully-qualified AcroForm field name to value; empty for documents with no form
 */
public record SignedDocument(
        String documentId,
        String name,
        String type,
        byte[] pdfBytes,
        Map<String, String> fields) {

    public SignedDocument {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
