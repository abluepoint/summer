package com.abluepoint.summer.sample.resource.infrastructure.discovery;

import com.abluepoint.summer.sample.resource.application.InterfaceResourceCatalogService;

@org.springframework.stereotype.Component
final class InterfaceResourceCatalogLifecycle implements org.springframework.beans.factory.SmartInitializingSingleton,
        org.springframework.context.ApplicationListener<org.springframework.context.event.ContextRefreshedEvent> {
    private final SpringInterfaceResourceDiscovery discovery;
    private final InterfaceResourceCatalogService service;

    InterfaceResourceCatalogLifecycle(SpringInterfaceResourceDiscovery discovery, InterfaceResourceCatalogService service) {
        this.discovery = discovery;
        this.service = service;
    }

    @Override public void afterSingletonsInstantiated() { service.refresh(); }

    @Override public void onApplicationEvent(org.springframework.context.event.ContextRefreshedEvent event) {
        if (discovery.registerContext(event.getApplicationContext())) service.refresh();
    }
}
