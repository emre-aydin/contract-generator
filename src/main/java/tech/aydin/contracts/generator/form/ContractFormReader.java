package tech.aydin.contracts.generator.form;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import tech.aydin.contracts.generator.ContractGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the current values of AcroForm fields from a PDF, keyed by fully-qualified field name.
 *
 * <p>This is the inverse of the generate pipeline: {@link ContractGenerator}
 * produces a PDF whose fillable regions are named AcroForm fields (e.g. {@code party.name},
 * {@code sig.date}, {@code agree.terms}); this reader extracts whatever values a user has since
 * typed or selected into those fields.
 *
 * <p>Value conventions:
 * <ul>
 *   <li>Text / textarea / choice fields: the value as typed, or {@code ""} if unfilled.</li>
 *   <li>Checkboxes: normalized to {@code "true"} / {@code "false"} (PDFBox otherwise returns the
 *       on-value, e.g. {@code "Yes"}, when checked and {@code "Off"} when not).</li>
 *   <li>Radio buttons: the selected option's export value, or {@code ""} if none selected.</li>
 * </ul>
 *
 * <p>Non-terminal fields (parent nodes created implicitly by dotted names, e.g. {@code party}
 * for {@code party.name}) are skipped; only leaf/terminal fields are returned. A PDF with no
 * AcroForm at all (e.g. a plain, non-fillable PDF) yields an empty map rather than throwing.
 */
public final class ContractFormReader {

    /**
     * Reads field values from PDF bytes.
     *
     * @param pdfBytes the PDF to read
     * @return field name to value, in field-tree encounter order; empty if there is no AcroForm
     */
    public Map<String, String> read(byte[] pdfBytes) {
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            return readValues(doc);
        } catch (IOException e) {
            throw new ReadException("Failed to read AcroForm values from PDF bytes", e);
        }
    }

    /**
     * Reads field values from a PDF input stream. The stream is fully consumed but not closed
     * by this method; the caller owns its lifecycle.
     */
    public Map<String, String> read(InputStream pdfStream) {
        try (PDDocument doc = PDDocument.load(pdfStream)) {
            return readValues(doc);
        } catch (IOException e) {
            throw new ReadException("Failed to read AcroForm values from PDF stream", e);
        }
    }

    /**
     * Reads field values from a PDF file on disk.
     */
    public Map<String, String> read(Path pdfPath) {
        try (InputStream in = Files.newInputStream(pdfPath);
             PDDocument doc = PDDocument.load(in)) {
            return readValues(doc);
        } catch (IOException e) {
            throw new ReadException("Failed to read AcroForm values from " + pdfPath, e);
        }
    }

    private Map<String, String> readValues(PDDocument doc) {
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
        if (form == null) {
            return Collections.emptyMap();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (PDField field : form.getFieldTree()) {
            if (field instanceof PDNonTerminalField) {
                continue;
            }
            String name = field.getFullyQualifiedName();
            values.put(name, valueOf(field));
        }
        return values;
    }

    private String valueOf(PDField field) {
        if (field instanceof PDCheckBox checkBox) {
            return Boolean.toString(isChecked(checkBox));
        }
        String value = field.getValueAsString();
        return value == null ? "" : value;
    }

    /**
     * Determines whether a checkbox is checked by comparing its raw {@code /V} entry to its
     * on-appearance-state name.
     *
     * <p>This deliberately avoids {@link PDCheckBox#getValue()} / {@code isChecked()}: openhtmltopdf
     * writes checkbox fields with a single-entry {@code /Opt} export-value array containing an
     * empty string. PDFBox's own value resolution treats the numeric on-state (e.g. {@code "0"})
     * as an index into that array, silently mapping a checked box to {@code ""} instead of its
     * real on-value. Reading the raw COS name sidesteps that lookup entirely.
     */
    private boolean isChecked(PDCheckBox checkBox) {
        COSBase raw = checkBox.getCOSObject().getDictionaryObject(COSName.V);
        String rawValue = raw instanceof COSName cosName ? cosName.getName() : null;
        return rawValue != null && rawValue.equals(checkBox.getOnValue());
    }

    /** Thrown when the PDF cannot be loaded or its AcroForm cannot be read. */
    public static final class ReadException extends RuntimeException {
        public ReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
