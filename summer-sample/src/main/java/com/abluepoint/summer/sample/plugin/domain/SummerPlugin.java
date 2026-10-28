package com.abluepoint.summer.sample.plugin.domain;


/** Stable SPI implemented by trusted, application-shipped plugins. */
public interface SummerPlugin {
    PluginDescriptor descriptor();
}
