package tech.aydin.contracts.generator.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Default {@link ContractSigningWorkflow} implementation: generate the PDF, then send it to
 * DocuSign. Each step is a separate activity so Temporal retries them independently.
 */
public final class ContractSigningWorkflowImpl implements ContractSigningWorkflow {

    private final ContractActivities activities = Workflow.newActivityStub(
            ContractActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .build())
                    .build());

    @Override
    public String generateAndSend(ContractSigningRequest request) {
        byte[] pdf = activities.generatePdf(request);
        return activities.sendToDocuSign(pdf, request);
    }
}
