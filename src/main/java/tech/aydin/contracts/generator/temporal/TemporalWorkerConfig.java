package tech.aydin.contracts.generator.temporal;

import java.util.Map;

/**
 * Core Temporal connection settings for the worker, read from the environment variables that the
 * {@code temporal-worker-k8s} charm injects into the workload container.
 *
 * <p>Only the connection essentials are modeled here. The charm additionally supports
 * candid/google/OIDC auth, an encryption codec, Sentry, and Vault (all provided by the Python
 * {@code temporal-lib-py} framework); those are intentionally out of scope for this Java worker.
 *
 * @param target       the Temporal frontend address ({@code host:port}) from {@code TEMPORAL_HOST}
 * @param namespace    the Temporal namespace from {@code TEMPORAL_NAMESPACE}
 * @param taskQueue    the task queue to poll from {@code TEMPORAL_QUEUE}
 * @param tlsRootCaPem root CA certificate(s) (PEM) from {@code TEMPORAL_TLS_ROOT_CAS}, or
 *                     {@code null}/blank for a plaintext connection
 */
public record TemporalWorkerConfig(String target, String namespace, String taskQueue, String tlsRootCaPem) {

    private static final String DEFAULT_HOST = "localhost:7233";
    private static final String DEFAULT_NAMESPACE = "default";

    public TemporalWorkerConfig {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target (TEMPORAL_HOST) must not be blank");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace (TEMPORAL_NAMESPACE) must not be blank");
        }
        if (taskQueue == null || taskQueue.isBlank()) {
            throw new IllegalArgumentException("taskQueue (TEMPORAL_QUEUE) must not be blank");
        }
    }

    /** Whether TLS should be used (a root CA PEM was supplied). */
    public boolean usesTls() {
        return tlsRootCaPem != null && !tlsRootCaPem.isBlank();
    }

    /**
     * Builds a config from an environment map (typically {@code System.getenv()}).
     *
     * <p>{@code TEMPORAL_HOST} defaults to {@code localhost:7233} and {@code TEMPORAL_NAMESPACE}
     * to {@code default}; {@code TEMPORAL_QUEUE} is required. {@code TEMPORAL_TLS_ROOT_CAS} is
     * optional.
     */
    public static TemporalWorkerConfig fromEnv(Map<String, String> env) {
        String host = orDefault(env.get("TEMPORAL_HOST"), DEFAULT_HOST);
        String namespace = orDefault(env.get("TEMPORAL_NAMESPACE"), DEFAULT_NAMESPACE);
        String queue = env.get("TEMPORAL_QUEUE");
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("TEMPORAL_QUEUE must be set");
        }
        String tls = env.get("TEMPORAL_TLS_ROOT_CAS");
        String tlsPem = tls == null || tls.isBlank() ? null : tls;
        return new TemporalWorkerConfig(host, namespace, queue, tlsPem);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
