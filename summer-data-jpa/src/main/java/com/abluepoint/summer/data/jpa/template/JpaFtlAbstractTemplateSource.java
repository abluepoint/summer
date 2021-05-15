package com.abluepoint.summer.data.jpa.template;

import com.abluepoint.summer.data.jpa.JpaSummerException;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Locale;

public abstract class JpaFtlAbstractTemplateSource implements JpaTemplateSource {

    private static final Logger logger = LoggerFactory.getLogger(JpaFtlAbstractTemplateSource.class);
    private StringTemplateLoader stringTemplateLoader;
    private Configuration cfg;
    private boolean forceReload = true;

    private String charsetName = "UTF-8";

    public JpaFtlAbstractTemplateSource() {
        stringTemplateLoader = new StringTemplateLoader();
        cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        cfg.setEncoding(Locale.getDefault(), "UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
        cfg.setSharedVariable("where", new FreemarkerWhereDirective());
        cfg.setTemplateLoader(stringTemplateLoader);
    }

    /**
     * @param templateName test/export
     * @param param
     * @return
     * @throws Exception
     */
    public String getTemplateSql(String templateName, Object param) throws Exception {

        templateName = templateName.replace(".","/");

        Assert.isTrue(templateName.indexOf("/") > 0, "illegal template name");

        if (forceReload || stringTemplateLoader.findTemplateSource(templateName) == null) {
            loadTemplate(templateName, param);
        }

        Template template = cfg.getTemplate(templateName, "UTF-8");


        StringWriter writer = new StringWriter();
        template.process(param, writer);
        return writer.toString();

    }

    protected abstract void loadTemplate(String templateName, Object param) throws IOException;


    public StringTemplateLoader getStringTemplateLoader() {
        return stringTemplateLoader;
    }

    public void setStringTemplateLoader(StringTemplateLoader stringTemplateLoader) {
        this.stringTemplateLoader = stringTemplateLoader;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

    public boolean isForceReload() {
        return forceReload;
    }

    public void setForceReload(boolean forceReload) {
        this.forceReload = forceReload;
    }
}
