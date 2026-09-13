package tech.aydin.contracts.generator.docusign;

import java.util.List;

/**
 * Everything needed to send a generated contract PDF to DocuSign for signature.
 *
 * @param documentName the document's display name inside DocuSign (e.g. "Consulting Agreement")
 * @param pdfBytes     the AcroForm-bearing PDF produced by {@code ContractGenerator}
 * @param emailSubject the subject line of the signing-request email
 * @param signers      one or more signers, in routing order; must not be empty
 */
public record SendRequest(String documentName, byte[] pdfBytes, String emailSubject, List<Signer> signers) {

    public SendRequest {
        if (documentName == null || documentName.isBlank()) {
            throw new IllegalArgumentException("documentName must not be blank");
        }
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("pdfBytes must not be empty");
        }
        if (emailSubject == null || emailSubject.isBlank()) {
            throw new IllegalArgumentException("emailSubject must not be blank");
        }
        if (signers == null || signers.isEmpty()) {
            throw new IllegalArgumentException("signers must not be empty");
        }
        signers = List.copyOf(signers);
    }
}
