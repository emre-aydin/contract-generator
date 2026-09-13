package tech.aydin.contracts.generator.docusign;

import com.docusign.esign.model.Document;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.Signer;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvelopeFactoryTest {

    private static final byte[] FAKE_PDF = "%PDF-1.4 fake contract bytes".getBytes();

    @Test
    void buildsEnvelopeWithTransformPdfFieldsAndSingleSigner() {
        SendRequest request = new SendRequest(
                "Consulting Agreement",
                FAKE_PDF,
                "Please sign: Consulting Agreement",
                List.of(new tech.aydin.contracts.generator.docusign.Signer("Jane Doe", "jane@example.com")));

        EnvelopeDefinition envelope = new EnvelopeFactory().build(request);

        assertEquals("sent", envelope.getStatus());
        assertEquals("Please sign: Consulting Agreement", envelope.getEmailSubject());

        assertEquals(1, envelope.getDocuments().size());
        Document document = envelope.getDocuments().getFirst();
        assertEquals("Consulting Agreement", document.getName());
        assertEquals("pdf", document.getFileExtension());
        assertEquals("true", document.getTransformPdfFields());
        assertEquals(Base64.getEncoder().encodeToString(FAKE_PDF), document.getDocumentBase64());

        List<Signer> signers = envelope.getRecipients().getSigners();
        assertEquals(1, signers.size());
        Signer signer = signers.getFirst();
        assertEquals("Jane Doe", signer.getName());
        assertEquals("jane@example.com", signer.getEmail());
        assertEquals("1", signer.getRoutingOrder());
    }

    @Test
    void assignsDistinctRecipientIdsForMultipleSigners() {
        SendRequest request = new SendRequest(
                "Agreement", FAKE_PDF, "Please sign",
                List.of(
                        new tech.aydin.contracts.generator.docusign.Signer("Alice", "alice@example.com", 1),
                        new tech.aydin.contracts.generator.docusign.Signer("Bob", "bob@example.com", 2)));

        EnvelopeDefinition envelope = new EnvelopeFactory().build(request);
        List<Signer> signers = envelope.getRecipients().getSigners();

        assertEquals(2, signers.size());
        assertEquals("1", signers.get(0).getRecipientId());
        assertEquals("2", signers.get(1).getRecipientId());
        assertNotEquals(signers.get(0).getRecipientId(), signers.get(1).getRecipientId());
        assertEquals("1", signers.get(0).getRoutingOrder());
        assertEquals("2", signers.get(1).getRoutingOrder());
    }
}
