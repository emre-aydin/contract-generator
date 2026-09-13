package tech.aydin.contracts.generator.docusign;

import com.docusign.esign.model.EnvelopeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DocuSignSenderTest {

    private static final byte[] FAKE_PDF = "%PDF-1.4 fake contract bytes".getBytes();

    @Test
    void buildsEnvelopeAndDelegatesToClientReturningEnvelopeId() {
        AtomicReference<EnvelopeDefinition> captured = new AtomicReference<>();
        DocuSignClient fakeClient = envelope -> {
            captured.set(envelope);
            return "envelope-123";
        };

        DocuSignSender sender = new DocuSignSender(new EnvelopeFactory(), fakeClient);
        SendRequest request = new SendRequest(
                "Consulting Agreement", FAKE_PDF, "Please sign",
                List.of(new Signer("Jane Doe", "jane@example.com")));

        String envelopeId = sender.sendForSignature(request);

        assertEquals("envelope-123", envelopeId);
        assertNotNull(captured.get(), "the client should receive the built envelope");
        assertEquals("sent", captured.get().getStatus());
        assertEquals(1, captured.get().getDocuments().size());
    }

    @Test
    void usesSameEnvelopeFactoryOutputPassedThrough() {
        EnvelopeFactory factory = new EnvelopeFactory();
        SendRequest request = new SendRequest(
                "Agreement", FAKE_PDF, "Please sign",
                List.of(new Signer("Alice", "alice@example.com")));
        EnvelopeDefinition expected = factory.build(request);

        DocuSignClient fakeClient = envelope -> {
            assertEquals(expected.getEmailSubject(), envelope.getEmailSubject());
            assertEquals(expected.getDocuments().getFirst().getDocumentBase64(),
                    envelope.getDocuments().getFirst().getDocumentBase64());
            return "envelope-456";
        };

        DocuSignSender sender = new DocuSignSender(new EnvelopeFactory(), fakeClient);
        assertEquals("envelope-456", sender.sendForSignature(request));
    }
}
