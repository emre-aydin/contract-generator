package tech.aydin.contracts.generator.docusign.webhook;

import tech.aydin.contracts.generator.form.ContractFormReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Parses a DocuSign Connect notification (JSON "aggregate" / eSignature Connect format) into a
 * {@link SignedEnvelope}, extracting each attached document's signed PDF and its AcroForm field
 * values. This is the library entry point a webhook endpoint would call with the raw request body.
 *
 * <p>Expected payload shape (only the used fields shown):
 * <pre>{@code
 * {
 *   "event": "envelope-completed",
 *   "data": {
 *     "envelopeId": "...",
 *     "envelopeSummary": {
 *       "status": "completed",
 *       "envelopeDocuments": [
 *         { "documentId": "1", "name": "Agreement", "type": "content", "PDFBytes": "<base64>" },
 *         { "documentId": "certificate", "name": "Summary", "type": "summary", "PDFBytes": "..." }
 *       ]
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>The signed PDF is only present when the Connect configuration has "Include Documents" enabled
 * and the envelope has reached completion. Per the parse-regardless contract, a payload without
 * documents parses successfully into a {@link SignedEnvelope} with an empty document list; only
 * genuinely malformed JSON (or a missing {@code data} object) raises {@link WebhookParseException}.
 */
public final class ConnectWebhookParser {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContractFormReader formReader = new ContractFormReader();

    /** Parses the raw request body bytes. */
    public SignedEnvelope parse(byte[] payload) {
        if (payload == null) {
            throw new WebhookParseException("payload must not be null");
        }
        return parse(new String(payload, StandardCharsets.UTF_8));
    }

    /** Parses the request body as a JSON string. */
    public SignedEnvelope parse(String json) {
        if (json == null || json.isBlank()) {
            throw new WebhookParseException("payload must not be empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new WebhookParseException("Payload is not valid JSON", e);
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw new WebhookParseException("Payload is missing the 'data' object");
        }

        String envelopeId = text(data, "envelopeId");
        JsonNode summary = data.get("envelopeSummary");
        String status = summary == null ? null : text(summary, "status");

        List<SignedDocument> documents = new ArrayList<>();
        if (summary != null) {
            JsonNode envelopeDocuments = summary.get("envelopeDocuments");
            if (envelopeDocuments != null && envelopeDocuments.isArray()) {
                for (JsonNode documentNode : envelopeDocuments) {
                    SignedDocument document = toSignedDocument(documentNode);
                    if (document != null) {
                        documents.add(document);
                    }
                }
            }
        }

        return new SignedEnvelope(envelopeId, status, documents);
    }

    private SignedDocument toSignedDocument(JsonNode documentNode) {
        String base64 = text(documentNode, "PDFBytes");
        if (base64 == null || base64.isBlank()) {
            return null;
        }

        byte[] pdfBytes;
        try {
            pdfBytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new WebhookParseException(
                    "Document '" + text(documentNode, "documentId") + "' has invalid base64 PDFBytes", e);
        }

        Map<String, String> fields = formReader.read(pdfBytes);
        return new SignedDocument(
                text(documentNode, "documentId"),
                text(documentNode, "name"),
                text(documentNode, "type"),
                pdfBytes,
                fields);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
