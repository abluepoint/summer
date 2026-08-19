package com.abluepoint.summer.data.jpa.template;

public interface SqlTemplateSource {

    String load(String location, String templateName);
}
