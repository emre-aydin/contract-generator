package tech.aydin.contracts.generator.form;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates and normalizes the AcroForm produced by openhtmltopdf so the resulting PDF is
 * ready to hand off to a signing service.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Enforce the {@link FieldNaming} convention and reject duplicate field names.</li>
 *   <li>Set {@code NeedAppearances = true} so viewers/signing services regenerate the visual
 *       appearance of values typed into the fields.</li>
 *   <li>Optionally assert that an expected set of fields is present.</li>
 *   <li>Optionally mark specific fields as required (PDF AcroForm Required flag). When the PDF
 *       is later sent to DocuSign with {@code transformPdfFields=true}, this flag carries over
 *       to the auto-converted tab's required attribute.</li>
 * </ul>
 */
public final class AcroFormPostProcessor {

    /**
     * Post-processes the given PDF bytes.
     *
     * @param pdfBytes            the rendered PDF
     * @param expectedFieldNames  field names that must be present; pass an empty set to skip
     * @return normalized PDF bytes
     */
    public byte[] process(byte[] pdfBytes, Set<String> expectedFieldNames) {
        return process(pdfBytes, expectedFieldNames, Set.of());
    }

    /**
     * Post-processes the given PDF bytes, optionally marking some fields as required.
     *
     * @param pdfBytes            the rendered PDF
     * @param expectedFieldNames  field names that must be present; pass an empty set to skip
     * @param requiredFieldNames  field names to mark as required (AcroForm Required flag);
     *                            pass an empty set to leave all fields optional
     * @return normalized PDF bytes
     */
    public byte[] process(byte[] pdfBytes, Set<String> expectedFieldNames, Set<String> requiredFieldNames) {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            if (form == null) {
                throw new PostProcessException(
                        "PDF has no AcroForm; the template must contain named HTML form controls.");
            }

            form.setNeedAppearances(true);

            Set<String> seen = new HashSet<>();
            List<String> present = new ArrayList<>();
            Map<String, PDField> byName = new HashMap<>();
            for (PDField field : form.getFieldTree()) {
                String name = field.getFullyQualifiedName();
                FieldNaming.requireValid(name);
                if (!seen.add(name)) {
                    throw new PostProcessException("Duplicate AcroForm field name: " + name);
                }
                present.add(name);
                byName.put(name, field);
            }

            if (!expectedFieldNames.isEmpty()) {
                Set<String> missing = new HashSet<>(expectedFieldNames);
                present.forEach(missing::remove);
                if (!missing.isEmpty()) {
                    throw new PostProcessException("Expected form fields are missing: " + missing);
                }
            }

            if (!requiredFieldNames.isEmpty()) {
                Set<String> missing = new HashSet<>(requiredFieldNames);
                present.forEach(missing::remove);
                if (!missing.isEmpty()) {
                    throw new PostProcessException("Fields marked required are missing: " + missing);
                }
                for (String name : requiredFieldNames) {
                    byName.get(name).setRequired(true);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new PostProcessException("Failed to post-process AcroForm", e);
        }
    }

    /** Thrown when the AcroForm is missing, malformed, or fails validation. */
    public static final class PostProcessException extends RuntimeException {
        public PostProcessException(String message) {
            super(message);
        }

        public PostProcessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
