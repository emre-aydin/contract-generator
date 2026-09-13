package tech.aydin.contracts.generator.docusign.webhook;

import tech.aydin.contracts.generator.ContractGenerator;
import tech.aydin.contracts.generator.model.ContractData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConnectWebhookParserTest {

    private final ConnectWebhookParser parser = new ConnectWebhookParser();
    private final ObjectMapper mapper = new ObjectMapper();

    private ContractData sampleData() {
        return ContractData.builder()
                .put("title", "Consulting Services Agreement")
                .put("provider", "Acme Consulting LLC")
                .put("client", "Globex Corporation")
                .put("effectiveDate", "2026-09-13")
                .put("recital", "The Provider agrees to deliver consulting services to the Client.")
                .build();
    }

    private byte[] filledContractPdf() throws IOException {
        byte[] generated = new ContractGenerator().generate("contract", sampleData());
        try (PDDocument doc = PDDocument.load(generated)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            form.getField("party.name").setValue("Jane Doe");
            form.getField("sig.date").setValue("2026-09-14");
            checkCheckbox((PDCheckBox) form.getField("agree.terms"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void checkCheckbox(PDCheckBox checkBox) {
        COSName onValue = COSName.getPDFName(checkBox.getOnValue());
        checkBox.getCOSObject().setItem(COSName.V, onValue);
        for (PDAnnotationWidget widget : checkBox.getWidgets()) {
            widget.getCOSObject().setItem(COSName.AS, onValue);
        }
    }

    private byte[] plainPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String completedPayload(byte[] contentPdf, byte[] summaryPdf) {
        ObjectNode root = mapper.createObjectNode();
        root.put("event", "envelope-completed");
        ObjectNode data = root.putObject("data");
        data.put("envelopeId", "abc-123");
        ObjectNode summary = data.putObject("envelopeSummary");
        summary.put("status", "completed");
        ArrayNode documents = summary.putArray("envelopeDocuments");

        ObjectNode content = documents.addObject();
        content.put("documentId", "1");
        content.put("name", "Consulting Services Agreement");
        content.put("type", "content");
        content.put("PDFBytes", Base64.getEncoder().encodeToString(contentPdf));

        ObjectNode certificate = documents.addObject();
        certificate.put("documentId", "certificate");
        certificate.put("name", "Summary");
        certificate.put("type", "summary");
        certificate.put("PDFBytes", Base64.getEncoder().encodeToString(summaryPdf));

        return root.toString();
    }

    @Test
    void parsesCompletedEnvelopeWithSignedFormValues() throws IOException {
        String payload = completedPayload(filledContractPdf(), plainPdf());

        SignedEnvelope envelope = parser.parse(payload);

        assertEquals("abc-123", envelope.envelopeId());
        assertEquals("completed", envelope.status());
        assertTrue(envelope.isCompleted());
        assertEquals(2, envelope.documents().size());

        SignedDocument content = envelope.documents().getFirst();
        assertEquals("1", content.documentId());
        assertEquals("content", content.type());
        assertEquals("Jane Doe", content.fields().get("party.name"));
        assertEquals("2026-09-14", content.fields().get("sig.date"));
        assertEquals("true", content.fields().get("agree.terms"));
        assertEquals("", content.fields().get("party.email"), "unfilled field reads as empty");

        SignedDocument summary = envelope.documents().get(1);
        assertEquals("certificate", summary.documentId());
        assertTrue(summary.fields().isEmpty(), "summary document has no AcroForm");

        // Merged accessor exposes the content document's values.
        Map<String, String> merged = envelope.fields();
        assertEquals("Jane Doe", merged.get("party.name"));
    }

    @Test
    void parsesRegardlessOfStatusWhenNoDocuments() {
        String payload = """
                {
                  "event": "envelope-sent",
                  "data": {
                    "envelopeId": "no-docs-1",
                    "envelopeSummary": { "status": "sent" }
                  }
                }
                """;

        SignedEnvelope envelope = parser.parse(payload);

        assertEquals("no-docs-1", envelope.envelopeId());
        assertEquals("sent", envelope.status());
        assertFalse(envelope.isCompleted());
        assertTrue(envelope.documents().isEmpty());
        assertTrue(envelope.fields().isEmpty());
    }

    @Test
    void parsesBytesOverload() throws IOException {
        String payload = completedPayload(filledContractPdf(), plainPdf());
        SignedEnvelope envelope = parser.parse(payload.getBytes());
        assertEquals("abc-123", envelope.envelopeId());
    }

    @Test
    void malformedJsonThrows() {
        assertThrows(WebhookParseException.class, () -> parser.parse("{not valid json"));
    }

    @Test
    void missingDataObjectThrows() {
        assertThrows(WebhookParseException.class, () -> parser.parse("{\"event\":\"x\"}"));
    }

    @Test
    void invalidBase64DocumentThrows() {
        String payload = """
                {
                  "data": {
                    "envelopeId": "bad-doc",
                    "envelopeSummary": {
                      "status": "completed",
                      "envelopeDocuments": [
                        { "documentId": "1", "name": "x", "type": "content", "PDFBytes": "@@@notbase64@@@" }
                      ]
                    }
                  }
                }
                """;
        assertThrows(WebhookParseException.class, () -> parser.parse(payload));
    }
}
