package tech.aydin.contracts.generator;

import tech.aydin.contracts.generator.model.ContractData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractGeneratorTest {

    private static final Set<String> EXPECTED_FIELDS = Set.of(
            "party.name", "party.email", "party.notes",
            "agree.terms", "sig.name", "sig.title", "sig.date");

    private ContractData sampleData() {
        return ContractData.builder()
                .put("title", "Consulting Services Agreement")
                .put("provider", "Acme Consulting LLC")
                .put("client", "Globex Corporation")
                .put("effectiveDate", "2026-09-13")
                .put("recital", "The Provider agrees to deliver consulting services to the Client.")
                .build();
    }

    @Test
    void generatesPdfWithExpectedFillableFields() throws IOException {
        byte[] pdf = new ContractGenerator().generate("contract", sampleData(), EXPECTED_FIELDS);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);

        try (PDDocument doc = PDDocument.load(pdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            assertNotNull(form, "PDF should contain an AcroForm");
            assertTrue(form.getNeedAppearances(), "NeedAppearances should be set");

            Map<String, PDField> byName = new HashMap<>();
            for (PDField field : form.getFieldTree()) {
                byName.put(field.getFullyQualifiedName(), field);
            }
            assertTrue(byName.keySet().containsAll(EXPECTED_FIELDS),
                    "All expected fields present; got " + byName.keySet());

            PDField name = byName.get("party.name");
            assertInstanceOf(PDTextField.class, name);
            assertFalse(name.isReadOnly(), "text field should be fillable");

            assertInstanceOf(PDCheckBox.class, byName.get("agree.terms"));
        }
    }

    @Test
    void rendersDynamicTemplateContent() throws IOException {
        byte[] pdf = new ContractGenerator().generate("contract", sampleData());
        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Consulting Services Agreement"), "title rendered");
            assertTrue(text.contains("Globex Corporation"), "client rendered");
        }
    }

    @Test
    void missingExpectedFieldFails() {
        Set<String> withUnknown = Set.of("party.name", "does.not.exist");
        var generator = new ContractGenerator();
        var data = sampleData();
        assertEquals(
                "Expected form fields are missing: [does.not.exist]",
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> generator.generate("contract", data, withUnknown)).getMessage());
    }

    @Test
    void marksRequestedFieldsAsRequired() throws IOException {
        Set<String> required = Set.of("party.name", "sig.date", "agree.terms");
        byte[] pdf = new ContractGenerator().generate("contract", sampleData(), EXPECTED_FIELDS, required);

        try (PDDocument doc = PDDocument.load(pdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            Map<String, PDField> byName = new HashMap<>();
            for (PDField field : form.getFieldTree()) {
                byName.put(field.getFullyQualifiedName(), field);
            }

            for (String name : required) {
                assertTrue(byName.get(name).isRequired(), name + " should be required");
            }
            assertFalse(byName.get("party.email").isRequired(), "party.email should stay optional");
            assertFalse(byName.get("sig.name").isRequired(), "sig.name should stay optional");
        }
    }

    @Test
    void unknownRequiredFieldFails() {
        Set<String> withUnknown = Set.of("does.not.exist");
        var generator = new ContractGenerator();
        var data = sampleData();
        assertEquals(
                "Fields marked required are missing: [does.not.exist]",
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () -> generator.generate("contract", data, Set.of(), withUnknown)).getMessage());
    }
}
