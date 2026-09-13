package tech.aydin.contracts.generator.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractSigningWorkflowTest {

    private static final String TASK_QUEUE = "test-contracts";

    private TestWorkflowEnvironment env;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        worker = env.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ContractSigningWorkflowImpl.class);
        client = env.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    void generatesThenSendsAndReturnsEnvelopeId() {
        byte[] cannedPdf = {1, 2, 3, 4};
        AtomicReference<byte[]> pdfSeenBySend = new AtomicReference<>();
        AtomicReference<ContractSigningRequest> generateRequest = new AtomicReference<>();

        ContractActivities fakeActivities = new ContractActivities() {
            @Override
            public byte[] generatePdf(ContractSigningRequest request) {
                generateRequest.set(request);
                return cannedPdf;
            }

            @Override
            public String sendToDocuSign(byte[] pdf, ContractSigningRequest request) {
                pdfSeenBySend.set(pdf);
                return "envelope-123";
            }
        };
        worker.registerActivitiesImplementations(fakeActivities);
        env.start();

        ContractSigningWorkflow workflow = client.newWorkflowStub(
                ContractSigningWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());

        ContractSigningRequest request = new ContractSigningRequest(
                "contract",
                Map.of("partyName", "Acme"),
                Set.of("signatureField"),
                Set.of("signatureField"),
                "Consulting Agreement",
                "Please sign",
                List.of(new SignerInfo("Jane Doe", "jane@example.com")));

        String envelopeId = workflow.generateAndSend(request);

        assertEquals("envelope-123", envelopeId);
        assertEquals("contract", generateRequest.get().templateName());
        assertArrayEquals(cannedPdf, pdfSeenBySend.get());
    }
}
