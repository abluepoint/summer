package com.abluepoint.summer.data.jpa.template;

import com.abluepoint.summer.data.jpa.JpaSummerException;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JpaFreemarkerTemplateSource implements JpaTemplateSource, ResourceLoaderAware, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(JpaFreemarkerTemplateSource.class);
    /**
     * spring 加载资源
     */
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    //	private String templateLocation = "classpath:config/sql";
    private String templateLocation = "classpath*:/sql/mysql";
    private String suffix = ".sql";
    private Configuration cfg;
    private StringTemplateLoader stringTemplateLoader;
    private String charsetName = "UTF-8";

    private Map<String, Object> loadedMap = new ConcurrentHashMap<>();
    //	private Set<String> loaded = new ConcurrentHashSet<>();
    /**
     * 是否开启缓存
     */
    private boolean enableCache = false;

    @Override
    public void afterPropertiesSet() {
        cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        stringTemplateLoader = new StringTemplateLoader();
        cfg.setTemplateLoader(stringTemplateLoader);
        cfg.setEncoding(Locale.getDefault(), "UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.IGNORE_HANDLER);
        cfg.setSharedVariable("where",new FtlWhereDirective());
    }

    /**
     * 通过resource加载模板
     *
     * @param resource
     * @param templateName
     * @return
     * @throws Exception
     */
    protected final String loadTemplateSrc(Resource resource, String templateName) throws Exception {
        logger.debug("load {} from {}", templateName, resource.getFilename());
        InputStream is = resource.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, charsetName));
        try {
            String templateSrc = loadTemplateSrc(reader, templateName);
            return templateSrc;
        } finally {
            try {
                reader.close();
            } catch (Exception e) {

            }
            try {
                is.close();
            } catch (Exception e) {

            }
        }
    }

    protected final String loadTemplateSrc(String location, String templateName) throws Exception {
        //		Resource resource = getTemplateResource(location);
        StringBuilder sourcePathBuilder = new StringBuilder(templateLocation);
        if(!templateLocation.endsWith("/")){
            sourcePathBuilder.append("/");
        }
        if (location.startsWith("/")) {
            location = location.substring(1);
        }
        sourcePathBuilder.append(location).append(suffix);
        String sourcePattern = sourcePathBuilder.toString();

        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        Resource[] resources = resourcePatternResolver.getResources(sourcePattern);
        String templateSrc = null;
        if (resources.length > 1) {
            logger.warn("sql location \"{}\" duplicated ", location);
        }
        for (Resource resource : resources) {
            templateSrc = loadTemplateSrc(resource, templateName);
            if (templateSrc != null) {
                break;
            }
        }

        return templateSrc;
    }

    protected final String loadTemplateSrc(BufferedReader reader, String templateName) throws IOException {
        String line = reader.readLine();
        if (line != null) {
            if (line.startsWith("--")) {
                String templateSrcName = StringUtils.trimWhitespace(line.substring(2));
                if (templateSrcName.equals(templateName)) {
                    StringBuilder templateSrcBuilder = new StringBuilder();
                    loadTemplateContent(reader, templateSrcBuilder);
                    return templateSrcBuilder.toString();
                } else {
                    return loadTemplateSrc(reader, templateName);
                }
            } else {
                return loadTemplateSrc(reader, templateName);
            }
        }
        return null;
    }

    protected final void loadTemplateContent(BufferedReader reader, StringBuilder context) throws IOException {
        String line = reader.readLine();
        if (line != null) {
            if (!line.startsWith("--")) {
                line = StringUtils.trimWhitespace(line);
                if (!line.equals("")) {
                    context.append(line).append(" ");
                }
                loadTemplateContent(reader, context);
            }
        }
    }

    /**
     *  以文件为准
     */
    public void loadTemplateSource(String location, String templateName) throws Exception {
        StringBuilder templateKeyBuilder = new StringBuilder(location);
        if(!location.endsWith("/")){
            templateKeyBuilder.append("/");
        }
        String templateKey = templateKeyBuilder.append(templateName).toString();
        Object source = stringTemplateLoader.findTemplateSource(templateKey);
        if (source == null) {
            String content = loadTemplateSrc(location, templateName);
            if(content==null){
                throw new JpaSummerException(new StringBuilder("Template \"").append(templateKey).append("\" not found!").toString());
            }
            stringTemplateLoader.putTemplate(templateKey, content);

        }
    }

    /**
     * @param templateName
     * @param param
     * @return
     * @throws Exception
     */
    public String getTemplate(String templateName, Object param) throws Exception {
        if (enableCache) {
            if (!loadedMap.containsKey(templateName)) {
                loadTemplate(templateName);
                loadedMap.put(templateName,null);
            }
        } else {
            loadTemplate(templateName);
        }

        StringWriter writer = new StringWriter();
        cfg.getTemplate(templateName, charsetName).process(param, writer);
        return writer.toString();
    }

    private void loadTemplate(String templateName) throws Exception {
        int pos = templateName.lastIndexOf("/");
        if (templateName != null && pos != -1) {
            String location = templateName.substring(0,pos);
            String tName = templateName.substring(pos+1);
            loadTemplateSource(location, tName);
        }
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

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

}
