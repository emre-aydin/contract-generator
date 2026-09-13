package tech.aydin.contracts.generator.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Typed holder for the dynamic values injected into a contract template.
 *
 * <p>These values populate the <em>layout/content</em> of the contract (party names, dates,
 * clauses). They are intentionally separate from the fillable regions, which are declared as
 * HTML form controls in the template and become AcroForm fields in the PDF.
 */
public final class ContractData {

    private final Map<String, Object> values;

    private ContractData(Map<String, Object> values) {
        this.values = values;
    }

    public static ContractData empty() {
        return new ContractData(new LinkedHashMap<>());
    }

    /** Returns a mutable builder for assembling contract data fluently. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns an immutable view of the backing values for template binding. */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Builder put(String key, Object value) {
            values.put(Objects.requireNonNull(key, "key"), value);
            return this;
        }

        public ContractData build() {
            return new ContractData(new LinkedHashMap<>(values));
        }
    }
}
