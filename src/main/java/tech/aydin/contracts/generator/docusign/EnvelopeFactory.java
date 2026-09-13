package tech.aydin.contracts.generator.docusign;

import com.docusign.esign.model.Document;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.Recipients;

import java.util.Base64;
import java.util.List;

/**
 * Builds a DocuSign {@link EnvelopeDefinition} from a {@link SendRequest}.
 *
 * <p>This class performs no network I/O and requires no DocuSign credentials, so it is fully
 * unit-testable. The document is attached with {@code transformPdfFields=true}, which tells
 * DocuSign to auto-convert the PDF's existing AcroForm fields into tabs; any field marked
 * required in the AcroForm (see {@code AcroFormPostProcessor}) becomes a required tab.
 */
public final class EnvelopeFactory {

    /** Builds an {@link EnvelopeDefinition} ready to pass to {@link DocuSignClient#createEnvelope}. */
    public EnvelopeDefinition build(SendRequest request) {
        Document document = new Document();
        document.setDocumentBase64(Base64.getEncoder().encodeToString(request.pdfBytes()));
        document.setName(request.documentName());
        document.setFileExtension("pdf");
        document.setDocumentId("1");
        document.setTransformPdfFields("true");

        List<com.docusign.esign.model.Signer> signers = new java.util.ArrayList<>();
        int recipientId = 1;
        for (Signer signer : request.signers()) {
            signers.add(toDocuSignSigner(signer, recipientId++));
        }

        Recipients recipients = new Recipients();
        recipients.setSigners(signers);

        EnvelopeDefinition envelope = new EnvelopeDefinition();
        envelope.setEmailSubject(request.emailSubject());
        envelope.setDocuments(List.of(document));
        envelope.setRecipients(recipients);
        envelope.setStatus("sent");
        return envelope;
    }

    private com.docusign.esign.model.Signer toDocuSignSigner(Signer signer, int recipientId) {
        com.docusign.esign.model.Signer docuSignSigner = new com.docusign.esign.model.Signer();
        docuSignSigner.setName(signer.name());
        docuSignSigner.setEmail(signer.email());
        docuSignSigner.setRecipientId(Integer.toString(recipientId));
        docuSignSigner.setRoutingOrder(Integer.toString(signer.routingOrder()));
        return docuSignSigner;
    }
}
