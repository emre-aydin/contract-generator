package tech.aydin.contracts.generator;

import tech.aydin.contracts.generator.form.AcroFormPostProcessor;
import tech.aydin.contracts.generator.form.ContractFormReader;
import tech.aydin.contracts.generator.model.ContractData;
import tech.aydin.contracts.generator.render.HtmlToPdfRenderer;
import tech.aydin.contracts.generator.template.ContractTemplateEngine;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Public entry point: turns a Thymeleaf contract template plus data into a DocuSign-ready
 * PDF whose fillable regions are interactive AcroForm fields, and reads back the values a
 * user has filled into such a PDF.
 *
 * <p>Generate pipeline: {@code Thymeleaf -> XHTML -> openhtmltopdf (form controls) -> PDFBox normalize}.
 * <p>Read pipeline (inverse): {@code PDF -> PDFBox AcroForm -> field name/value map}.
 *
 * <pre>{@code
 * byte[] pdf = new ContractGenerator().generate("contract", data);
 * // ... user fills in the PDF ...
 * Map<String, String> filled = new ContractGenerator().readValues(filledPdfBytes);
 * }</pre>
 */
public final class ContractGenerator {

    private final ContractTemplateEngine templateEngine;
    private final HtmlToPdfRenderer renderer;
    private final AcroFormPostProcessor postProcessor;
    private final ContractFormReader formReader;

    public ContractGenerator() {
        this(new ContractTemplateEngine(), new HtmlToPdfRenderer(), new AcroFormPostProcessor(),
                new ContractFormReader());
    }

    public ContractGenerator(ContractTemplateEngine templateEngine,
                             HtmlToPdfRenderer renderer,
                             AcroFormPostProcessor postProcessor) {
        this(templateEngine, renderer, postProcessor, new ContractFormReader());
    }

    public ContractGenerator(ContractTemplateEngine templateEngine,
                             HtmlToPdfRenderer renderer,
                             AcroFormPostProcessor postProcessor,
                             ContractFormReader formReader) {
        this.templateEngine = templateEngine;
        this.renderer = renderer;
        this.postProcessor = postProcessor;
        this.formReader = formReader;
    }

    /**
     * Generates the contract PDF.
     *
     * @param templateName template name under {@code templates/} (without {@code .html})
     * @param data         dynamic contract content
     * @return DocuSign-ready PDF bytes
     */
    public byte[] generate(String templateName, ContractData data) {
        return generate(templateName, data, Collections.emptySet());
    }

    /**
     * Generates the contract PDF and asserts the given fields are present in the AcroForm.
     *
     * @param expectedFieldNames field names that must exist in the output
     */
    public byte[] generate(String templateName, ContractData data, Set<String> expectedFieldNames) {
        return generate(templateName, data, expectedFieldNames, Collections.emptySet());
    }

    /**
     * Generates the contract PDF, asserts the given fields are present, and marks
     * {@code requiredFieldNames} as required (AcroForm Required flag). When the PDF is later
     * sent to DocuSign with {@code transformPdfFields=true}, required AcroForm fields become
     * required tabs automatically.
     *
     * @param expectedFieldNames field names that must exist in the output
     * @param requiredFieldNames field names to mark as required; must be a subset of the
     *                           fields present in the template
     */
    public byte[] generate(String templateName, ContractData data, Set<String> expectedFieldNames,
                           Set<String> requiredFieldNames) {
        var xhtml = templateEngine.render(templateName, data);
        var pdf = renderer.render(xhtml, null);
        return postProcessor.process(pdf, expectedFieldNames, requiredFieldNames);
    }

    /**
     * Reads the current AcroForm field values from a (possibly user-filled) contract PDF.
     *
     * @param pdfBytes the PDF to read
     * @return field name to value; empty if the PDF has no AcroForm
     */
    public Map<String, String> readValues(byte[] pdfBytes) {
        return formReader.read(pdfBytes);
    }

    /**
     * Reads the current AcroForm field values from a contract PDF input stream. The stream is
     * fully consumed but not closed; the caller owns its lifecycle.
     */
    public Map<String, String> readValues(InputStream pdfStream) {
        return formReader.read(pdfStream);
    }

    /**
     * Reads the current AcroForm field values from a contract PDF file on disk.
     */
    public Map<String, String> readValues(Path pdfPath) {
        return formReader.read(pdfPath);
    }
}
