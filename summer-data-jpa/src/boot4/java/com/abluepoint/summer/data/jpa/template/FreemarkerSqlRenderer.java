package com.abluepoint.summer.data.jpa.template;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

public final class FreemarkerSqlRenderer {

    private final Configuration configuration;

    public FreemarkerSqlRenderer() {
        configuration = new Configuration(Configuration.VERSION_2_3_34);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
    }

    public String render(String templateName, String sqlTemplate, Map<String, ?> parameters) {
        try {
            Template template = new Template(templateName, new StringReader(sqlTemplate), configuration);
            StringWriter output = new StringWriter();
            template.process(parameters == null ? Map.of() : parameters, output);
            return output.toString().strip();
        } catch (IOException | TemplateException exception) {
            throw new IllegalArgumentException("Unable to render SQL template '" + templateName + "'", exception);
        }
    }
}
