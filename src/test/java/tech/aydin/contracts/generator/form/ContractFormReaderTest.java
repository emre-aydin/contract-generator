package tech.aydin.contracts.generator.form;

import tech.aydin.contracts.generator.ContractGenerator;
import tech.aydin.contracts.generator.model.ContractData;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractFormReaderTest {

    private ContractData sampleData() {
        return ContractData.builder()
                .put("title", "Consulting Services Agreement")
                .put("provider", "Acme Consulting LLC")
                .put("client", "Globex Corporation")
                .put("effectiveDate", "2026-09-13")
                .put("recital", "The Provider agrees to deliver consulting services to the Client.")
                .build();
    }

    /** Simulates a user filling in the generated PDF via PDFBox. */
    private byte[] fillSampleFields(byte[] pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);

            form.getField("party.name").setValue("Jane Doe");
            form.getField("sig.date").setValue("2026-09-14");
            checkCheckbox((PDCheckBox) form.getField("agree.terms"));
            // party.email intentionally left unfilled.

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Checks a checkbox by setting its value and widget appearance state directly.
     *
     * <p>openhtmltopdf-generated checkboxes register their "on" appearance state (e.g.
     * {@code "0"}) without populating the field's {@code /Opt} export-value array, which makes
     * PDFBox's own {@link PDCheckBox#check()} reject that same on-value as invalid. Setting the
     * COS value and widget appearance state directly mirrors what a PDF viewer does when a user
     * clicks the box, bypassing that overly strict validation.
     */
    private void checkCheckbox(PDCheckBox checkBox) {
        COSName onValue = COSName.getPDFName(checkBox.getOnValue());
        checkBox.getCOSObject().setItem(COSName.V, onValue);
        for (PDAnnotationWidget widget : checkBox.getWidgets()) {
            widget.getCOSObject().setItem(COSName.AS, onValue);
        }
    }

    @Test
    void readsFilledAndUnfilledFieldValues() throws IOException {
        byte[] generated = new ContractGenerator().generate("contract", sampleData());
        byte[] filled = fillSampleFields(generated);

        Map<String, String> values = new ContractFormReader().read(filled);

        assertEquals("Jane Doe", values.get("party.name"));
        assertEquals("2026-09-14", values.get("sig.date"));
        assertEquals("true", values.get("agree.terms"));
        assertEquals("", values.get("party.email"), "unfilled text field should read as empty");

        // Only terminal fields are returned; dotted-name parents are excluded.
        assertTrue(values.keySet().stream().noneMatch(k -> k.equals("party") || k.equals("sig") || k.equals("agree")));
    }

    @Test
    void readsThroughContractGeneratorFacade() throws IOException {
        byte[] generated = new ContractGenerator().generate("contract", sampleData());
        byte[] filled = fillSampleFields(generated);

        Map<String, String> values = new ContractGenerator().readValues(filled);

        assertEquals("Jane Doe", values.get("party.name"));
        assertEquals("true", values.get("agree.terms"));
    }

    @Test
    void pdfWithoutAcroFormYieldsEmptyMap() throws IOException {
        byte[] plainPdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            plainPdf = out.toByteArray();
        }

        Map<String, String> values = new ContractFormReader().read(plainPdf);

        assertTrue(values.isEmpty());
    }
}
