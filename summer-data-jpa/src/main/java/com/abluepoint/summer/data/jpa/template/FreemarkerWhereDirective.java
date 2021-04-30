package com.abluepoint.summer.data.jpa.template;

import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

public class FreemarkerWhereDirective implements TemplateDirectiveModel {

    private static final String SPACE = " ";

    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body) throws TemplateException, IOException {

        Writer out = env.getOut();

        StringWriter sw = new StringWriter();
        body.render(sw);
        String bodyString = sw.toString();

        if (StringUtils.isNoneBlank(bodyString)) {
            out.write("where");
            out.write(SPACE);
            bodyString = StringUtils.trim(bodyString);

            int begin = 0;
            int end = bodyString.length();
            if (StringUtils.startsWithIgnoreCase(bodyString, "and")) {
                begin = 3;
            }

            if (StringUtils.endsWithIgnoreCase(bodyString, "and")) {
                end = end - 3;
            }

            bodyString = bodyString.substring(begin, end);

            out.write(StringUtils.trim(bodyString));
            out.write(SPACE);
        }

    }
}
