package tech.aydin.contracts.generator.render;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Renders an XHTML string to PDF bytes using openhtmltopdf.
 *
 * <p>Fast mode is enabled so that native HTML form controls in the markup
 * ({@code <input>}, {@code <textarea>}, {@code <select>}, checkbox/radio) are emitted as
 * interactive AcroForm fields rather than being flattened to static text. Each control's
 * {@code name} attribute becomes the AcroForm field's fully-qualified name.
 */
public final class HtmlToPdfRenderer {

    /**
     * CSS font-family name under which the bundled font is registered. openhtmltopdf ships with
     * no default font, and form controls in particular require a resolvable font, so templates
     * must use this family (see {@code contract.html}).
     */
    public static final String FONT_FAMILY = "ContractFont";

    private static final String FONT_RESOURCE = "/fonts/Contract-Regular.ttf";

    /**
     * @param xhtml   strict XHTML produced by the template engine
     * @param baseUri base URI used to resolve relative resources (may be {@code null})
     * @return the rendered PDF as a byte array
     */
    public byte[] render(String xhtml, String baseUri) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(this::openFont, FONT_FAMILY);
            builder.withHtmlContent(xhtml, baseUri);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RenderException("Failed to render HTML to PDF", e);
        }
    }

    private InputStream openFont() {
        InputStream in = HtmlToPdfRenderer.class.getResourceAsStream(FONT_RESOURCE);
        if (in == null) {
            throw new RenderException("Bundled font not found on classpath: " + FONT_RESOURCE, null);
        }
        return in;
    }

    /** Thrown when the underlying renderer fails. */
    public static final class RenderException extends RuntimeException {
        public RenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
