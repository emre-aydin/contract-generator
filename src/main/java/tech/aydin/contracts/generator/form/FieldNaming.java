package tech.aydin.contracts.generator.form;

import java.util.regex.Pattern;

/**
 * Conventions and validation for AcroForm field names.
 *
 * <p>Field names originate from the {@code name} attribute of HTML form controls in the template.
 * To keep the generated PDF interoperable with signing services such as DocuSign (which match
 * tabs by field name), names must be stable, unique, and restricted to a safe character set.
 *
 * <p>Allowed: ASCII letters, digits, dot, underscore and hyphen; must start with a letter.
 * The dot is permitted so hierarchical names like {@code party.name} can be used.
 */
public final class FieldNaming {

    private static final Pattern VALID = Pattern.compile("[A-Za-z][A-Za-z0-9._-]*");

    private FieldNaming() {
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    /**
     * Validates a field name, throwing if it violates the convention.
     */
    public static void requireValid(String name) {
        if (!isValid(name)) {
            throw new IllegalArgumentException(
                    "Invalid AcroForm field name '" + name + "'. Names must start with a letter "
                            + "and contain only letters, digits, '.', '_' or '-'.");
        }
    }
}
