package com.abluepoint.summer.data.jpa.query.spi;

public interface SqlTemplateSource {

    String load(String location, String templateName);
}
