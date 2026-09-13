package tech.aydin.contracts.generator.temporal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalWorkerConfigTest {

    @Test
    void readsAllValuesFromEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("TEMPORAL_HOST", "temporal.example:7233");
        env.put("TEMPORAL_NAMESPACE", "prod");
        env.put("TEMPORAL_QUEUE", "contracts");
        env.put("TEMPORAL_TLS_ROOT_CAS", "-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----");

        TemporalWorkerConfig config = TemporalWorkerConfig.fromEnv(env);

        assertEquals("temporal.example:7233", config.target());
        assertEquals("prod", config.namespace());
        assertEquals("contracts", config.taskQueue());
        assertTrue(config.usesTls());
    }

    @Test
    void appliesDefaultsForHostAndNamespace() {
        Map<String, String> env = new HashMap<>();
        env.put("TEMPORAL_QUEUE", "contracts");

        TemporalWorkerConfig config = TemporalWorkerConfig.fromEnv(env);

        assertEquals("localhost:7233", config.target());
        assertEquals("default", config.namespace());
        assertNull(config.tlsRootCaPem());
        assertFalse(config.usesTls());
    }

    @Test
    void requiresQueue() {
        Map<String, String> env = new HashMap<>();
        env.put("TEMPORAL_HOST", "temporal.example:7233");

        assertThrows(IllegalArgumentException.class, () -> TemporalWorkerConfig.fromEnv(env));
    }

    @Test
    void blankTlsIsTreatedAsNoTls() {
        Map<String, String> env = new HashMap<>();
        env.put("TEMPORAL_QUEUE", "contracts");
        env.put("TEMPORAL_TLS_ROOT_CAS", "   ");

        TemporalWorkerConfig config = TemporalWorkerConfig.fromEnv(env);

        assertFalse(config.usesTls());
        assertNull(config.tlsRootCaPem());
    }
}
