package tech.aydin.contracts.generator.temporal;

import com.sun.net.httpserver.HttpServer;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Entry point for the packaged Temporal worker (the {@code worker}-classifier uber-jar run by the
 * rock's {@code start-worker.sh}). It connects to the Temporal server described by the injected
 * {@code TEMPORAL_*} environment variables, registers {@link ContractSigningWorkflow} and
 * {@link ContractActivities} on the configured task queue, and blocks.
 *
 * <p>If {@code TEMPORAL_PROMETHEUS_PORT} is set, a minimal HTTP endpoint exposes worker metrics
 * in Prometheus format at {@code /metrics} on that port.
 */
public final class WorkerMain {

    private WorkerMain() {
    }

    static void main() throws Exception {
        TemporalWorkerConfig config = TemporalWorkerConfig.fromEnv(System.getenv());

        WorkflowServiceStubsOptions.Builder stubOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(config.target());
        if (config.usesTls()) {
            stubOptions.setSslContext(buildSslContext(config.tlsRootCaPem()));
        }

        String prometheusPort = System.getenv("TEMPORAL_PROMETHEUS_PORT");
        PrometheusMeterRegistry metricsRegistry;
        if (prometheusPort != null && !prometheusPort.isBlank()) {
            metricsRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            Scope metricsScope = new RootScopeBuilder()
                    .reporter(new MicrometerClientStatsReporter(metricsRegistry))
                    .reportEvery(com.uber.m3.util.Duration.ofSeconds(10));
            stubOptions.setMetricsScope(metricsScope);
            startMetricsServer(Integer.parseInt(prometheusPort.trim()), metricsRegistry);
        }

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(stubOptions.build());
        WorkflowClient client = WorkflowClient.newInstance(service,
                WorkflowClientOptions.newBuilder().setNamespace(config.namespace()).build());

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(config.taskQueue());
        worker.registerWorkflowImplementationTypes(ContractSigningWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ContractActivitiesImpl());

        System.out.printf("Starting contract-generator worker: target=%s namespace=%s queue=%s tls=%s%n",
                config.target(), config.namespace(), config.taskQueue(), config.usesTls());

        factory.start();

        Runtime.getRuntime().addShutdownHook(new Thread(factory::shutdown));
        // Block the main thread; the worker runs on factory-managed threads.
        Thread.currentThread().join();
    }

    private static SslContext buildSslContext(String rootCaPem) throws Exception {
        try (ByteArrayInputStream ca = new ByteArrayInputStream(
                rootCaPem.getBytes(StandardCharsets.UTF_8))) {
            return GrpcSslContexts.forClient().trustManager(ca).build();
        }
    }

    private static void startMetricsServer(int port, PrometheusMeterRegistry registry) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", exchange -> {
            byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.printf("Prometheus metrics available on :%d/metrics%n", port);
    }
}
