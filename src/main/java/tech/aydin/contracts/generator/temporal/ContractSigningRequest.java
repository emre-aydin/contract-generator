package tech.aydin.contracts.generator.temporal;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The input to {@link ContractSigningWorkflow}: which template to render, the parameters to render
 * it with, and the DocuSign delivery details. This is what a Temporal client supplies when it
 * starts the workflow.
 *
 * @param templateName        template name under {@code templates/} (without {@code .html}),
 *                            resolved from the classpath bundled inside the worker jar
 * @param parameters          dynamic template values (keys map to {@code ContractData} entries)
 * @param expectedFieldNames  AcroForm field names that must be present (may be empty)
 * @param requiredFieldNames  AcroForm field names to mark required (may be empty); these become
 *                            required DocuSign tabs via {@code transformPdfFields}
 * @param documentName        the document's display name inside DocuSign
 * @param emailSubject        the DocuSign signing-request email subject
 * @param signers             the envelope signers (at least one)
 */
public record ContractSigningRequest(
        String templateName,
        Map<String, String> parameters,
        Set<String> expectedFieldNames,
        Set<String> requiredFieldNames,
        String documentName,
        String emailSubject,
        List<SignerInfo> signers) {

    public ContractSigningRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        expectedFieldNames = expectedFieldNames == null ? Set.of() : Set.copyOf(expectedFieldNames);
        requiredFieldNames = requiredFieldNames == null ? Set.of() : Set.copyOf(requiredFieldNames);
        signers = signers == null ? List.of() : List.copyOf(signers);
    }
}
