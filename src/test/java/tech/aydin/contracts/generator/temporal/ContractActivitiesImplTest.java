package tech.aydin.contracts.generator.temporal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractActivitiesImplTest {

    @Test
    void generatePdfRendersBundledTemplateIntoAcroForm() throws IOException {
        ContractActivitiesImpl activities = new ContractActivitiesImpl();

        ContractSigningRequest request = new ContractSigningRequest(
                "contract",
                Map.of(
                        "title", "Consulting Services Agreement",
                        "provider", "Acme Consulting LLC",
                        "client", "Globex Corporation",
                        "effectiveDate", "2026-09-13",
                        "recital", "The Provider agrees to deliver consulting services."),
                Set.of("party.name", "party.email", "sig.name"),
                Set.of("party.name"),
                "Consulting Agreement",
                "Please sign",
                List.of(new SignerInfo("Jane Doe", "jane@example.com")));

        byte[] pdf = activities.generatePdf(request);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            assertNotNull(form, "generated PDF should contain an AcroForm");
            assertNotNull(form.getField("party.name"));
        }
    }
}
