package tech.aydin.contracts.generator.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Temporal workflow that renders a contract template into a fillable PDF and sends it to DocuSign
 * for signature, returning the created envelope id.
 */
@WorkflowInterface
public interface ContractSigningWorkflow {

    /**
     * Generates the contract PDF from the request's template + parameters, then sends it to
     * DocuSign for signature.
     *
     * @param request the template name, parameters, and DocuSign delivery details
     * @return the DocuSign envelope id
     */
    @WorkflowMethod
    String generateAndSend(ContractSigningRequest request);
}
