package de.benshu.jswizzle.internal;

import com.google.common.base.Throwables;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringWriter;

public class Template {
    private static final Configuration freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_22);

    static {
        freemarkerConfiguration.setClassLoaderForTemplateLoading(Template.class.getClassLoader(), "templates");
        freemarkerConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfiguration.setDefaultEncoding("UTF-8");
    }

    public static String render(String templateName, Object model) {
        try {
            final StringWriter writer = new StringWriter();

            freemarkerConfiguration.getTemplate(templateName)
                    .process(model, writer);

            return writer.toString();
        } catch (TemplateException | IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
