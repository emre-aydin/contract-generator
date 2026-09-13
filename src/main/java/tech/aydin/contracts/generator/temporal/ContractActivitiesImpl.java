package tech.aydin.contracts.generator.temporal;

import tech.aydin.contracts.generator.ContractGenerator;
import tech.aydin.contracts.generator.docusign.DocuSignConfig;
import tech.aydin.contracts.generator.docusign.DocuSignSender;
import tech.aydin.contracts.generator.docusign.SendRequest;
import tech.aydin.contracts.generator.docusign.Signer;
import tech.aydin.contracts.generator.model.ContractData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Default {@link ContractActivities} implementation. {@code generatePdf} delegates to
 * {@link ContractGenerator}; {@code sendToDocuSign} builds a {@link DocuSignConfig} from
 * {@code DOCUSIGN_*} environment variables and sends via {@link DocuSignSender}.
 *
 * <p>Recognised environment variables:
 * <ul>
 *   <li>{@code DOCUSIGN_ACCOUNT_ID}</li>
 *   <li>{@code DOCUSIGN_OAUTH_BASE_PATH} (e.g. {@code account-d.docusign.com})</li>
 *   <li>{@code DOCUSIGN_INTEGRATION_KEY}</li>
 *   <li>{@code DOCUSIGN_USER_ID}</li>
 *   <li>{@code DOCUSIGN_PRIVATE_KEY} (PEM contents) <em>or</em> {@code DOCUSIGN_PRIVATE_KEY_PATH}
 *       (path to a PEM file)</li>
 * </ul>
 */
public final class ContractActivitiesImpl implements ContractActivities {

    private final ContractGenerator generator;
    private final Map<String, String> env;
    private final Function<DocuSignConfig, DocuSignSender> senderFactory;

    /** Production wiring: real generator, {@code System.getenv()}, live DocuSign sender. */
    public ContractActivitiesImpl() {
        this(new ContractGenerator(), System.getenv(), DocuSignSender::forLiveDocuSign);
    }

    /** Testable wiring: inject the generator, environment, and DocuSign sender factory. */
    public ContractActivitiesImpl(ContractGenerator generator, Map<String, String> env,
                                  Function<DocuSignConfig, DocuSignSender> senderFactory) {
        this.generator = generator;
        this.env = env;
        this.senderFactory = senderFactory;
    }

    @Override
    public byte[] generatePdf(ContractSigningRequest request) {
        ContractData.Builder builder = ContractData.builder();
        request.parameters().forEach(builder::put);
        return generator.generate(request.templateName(), builder.build(),
                request.expectedFieldNames(), request.requiredFieldNames());
    }

    @Override
    public String sendToDocuSign(byte[] pdf, ContractSigningRequest request) {
        DocuSignConfig config = docuSignConfigFromEnv();
        List<Signer> signers = request.signers().stream()
                .map(s -> new Signer(s.name(), s.email(), s.routingOrder()))
                .toList();
        SendRequest sendRequest = new SendRequest(
                request.documentName(), pdf, request.emailSubject(), signers);
        return senderFactory.apply(config).sendForSignature(sendRequest);
    }

    private DocuSignConfig docuSignConfigFromEnv() {
        return new DocuSignConfig(
                env.get("DOCUSIGN_ACCOUNT_ID"),
                env.get("DOCUSIGN_OAUTH_BASE_PATH"),
                env.get("DOCUSIGN_INTEGRATION_KEY"),
                env.get("DOCUSIGN_USER_ID"),
                privateKeyBytes());
    }

    private byte[] privateKeyBytes() {
        String inline = env.get("DOCUSIGN_PRIVATE_KEY");
        if (inline != null && !inline.isBlank()) {
            return inline.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        String path = env.get("DOCUSIGN_PRIVATE_KEY_PATH");
        if (path != null && !path.isBlank()) {
            try {
                return Files.readAllBytes(Path.of(path));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to read DOCUSIGN_PRIVATE_KEY_PATH: " + path, e);
            }
        }
        throw new IllegalStateException(
                "DOCUSIGN_PRIVATE_KEY or DOCUSIGN_PRIVATE_KEY_PATH must be set");
    }
}
