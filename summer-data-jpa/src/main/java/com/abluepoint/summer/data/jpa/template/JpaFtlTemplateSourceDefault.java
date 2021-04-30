package com.abluepoint.summer.data.jpa.template;

import com.abluepoint.summer.common.util.ResourcesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JpaFtlTemplateSourceDefault extends JpaFtlAbstractTemplateSource implements ResourceLoaderAware {

    private static final Logger logger = LoggerFactory.getLogger(JpaFtlTemplateSourceDefault.class);

    private ResourceLoader resourceLoader;
    private String templateLocation = "classpath*:/sql/mysql";
    private String suffix = ".sql";

    @Override
    protected void loadTemplate(String templateName, Object param) throws IOException {
        int point = templateName.lastIndexOf("/");

        String innerTemplateName = templateName.substring(point + 1);

        String templateFileName = new StringBuilder(templateName.substring(0, point)).append(suffix).toString();
        String templateFilePath = ResourcesUtils.pathConcat(templateLocation, templateFileName);
        Resource[] resources = ResourcesUtils.getResources(templateFilePath, resourceLoader);

        if (resources.length > 1) {
            logger.warn("sql location \"{}\" duplicated ", templateFilePath);
        }
        String templateSrc = null;
        for (Resource resource : resources) {
            logger.debug("load {} from {}, lastModified {}", templateName, resource.getFilename());

            InputStream is = resource.getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, getCharsetName()))) {
                templateSrc = doRead(reader, innerTemplateName);
            }
            if (templateSrc != null) {
                getStringTemplateLoader().putTemplate(templateName, templateSrc);
                break;
            }

        }
    }


    private String doRead(BufferedReader reader, String templateName) throws IOException {
        String line = reader.readLine();
        if (line != null) {
            if (line.startsWith("--")) {
                String templateSrcName = StringUtils.trimWhitespace(line.substring(2));
                if (templateSrcName.equals(templateName)) {
                    StringBuilder templateSrcBuilder = new StringBuilder();
                    doReadContent(reader, templateSrcBuilder);
                    return templateSrcBuilder.toString();
                } else {
                    return doRead(reader, templateName);
                }
            } else {
                return doRead(reader, templateName);
            }
        }
        return null;
    }

    private void doReadContent(BufferedReader reader, StringBuilder context) throws IOException {
        String line = reader.readLine();
        if (line != null) {
            if (!line.startsWith("--")) {
                line = StringUtils.trimWhitespace(line);
                if (!line.equals("")) {
                    context.append(line).append(" ");
                }
                doReadContent(reader, context);
            }
        }
    }


    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String getTemplateLocation() {
        return templateLocation;
    }

    public void setTemplateLocation(String templateLocation) {
        this.templateLocation = templateLocation;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}
