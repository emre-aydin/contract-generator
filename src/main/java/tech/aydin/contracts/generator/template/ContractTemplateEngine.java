package tech.aydin.contracts.generator.template;

import tech.aydin.contracts.generator.model.ContractData;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;

/**
 * Thin wrapper around Thymeleaf that turns a classpath template plus {@link ContractData}
 * into an HTML string.
 *
 * <p>Templates are resolved from {@code classpath:templates/} and must be well-formed XML/XHTML,
 * because openhtmltopdf consumes strict XHTML.
 */
public final class ContractTemplateEngine {

    private final TemplateEngine engine;

    public ContractTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine e = new TemplateEngine();
        e.setTemplateResolver(resolver);
        this.engine = e;
    }

    /**
     * Renders the named template (without the {@code .html} suffix) to an HTML string.
     *
     * @param templateName template file name under {@code templates/}, e.g. {@code "contract"}
     * @param data         dynamic values to bind
     * @return rendered XHTML ready for {@code HtmlToPdfRenderer}
     */
    public String render(String templateName, ContractData data) {
        Context context = new Context(Locale.US);
        context.setVariables(data.asMap());
        return engine.process(templateName, context);
    }
}
