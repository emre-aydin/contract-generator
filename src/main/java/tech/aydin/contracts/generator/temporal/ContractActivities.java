package tech.aydin.contracts.generator.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * The side-effecting steps of {@link ContractSigningWorkflow}, split so Temporal can retry each
 * independently.
 */
@ActivityInterface
public interface ContractActivities {

    /**
     * Renders the request's template with its parameters into a DocuSign-ready AcroForm PDF.
     *
     * @return the generated PDF bytes
     */
    @ActivityMethod
    byte[] generatePdf(ContractSigningRequest request);

    /**
     * Sends the given PDF to DocuSign for signature using the request's document name, email
     * subject, and signers.
     *
     * @param pdf     the PDF bytes produced by {@link #generatePdf}
     * @param request the delivery details (document name, email subject, signers, required fields)
     * @return the DocuSign envelope id
     */
    @ActivityMethod
    String sendToDocuSign(byte[] pdf, ContractSigningRequest request);
}
