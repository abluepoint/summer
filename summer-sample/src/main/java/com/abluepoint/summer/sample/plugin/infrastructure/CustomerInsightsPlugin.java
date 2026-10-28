package com.abluepoint.summer.sample.plugin.infrastructure;
import com.abluepoint.summer.sample.plugin.domain.PluginDescriptor;
import com.abluepoint.summer.sample.plugin.domain.PluginConfiguration;
import com.abluepoint.summer.sample.plugin.domain.PluginMenu;
import com.abluepoint.summer.sample.plugin.domain.SummerPlugin;

import org.springframework.stereotype.Component;

import java.util.List;

/** Reference plugin shipped with Summer Sample; it is not an upload endpoint. */
@Component
public final class CustomerInsightsPlugin implements SummerPlugin {
    private static final PluginDescriptor DESCRIPTOR = new PluginDescriptor(
            "customer-insights", "Customer insights", "1.0.0", "sha256:customer-insights-v1", "4.x",
            List.of("enterprise.plugins.read", "enterprise.plugins.customer-insights.read"),
            List.of(new PluginMenu("customer-insights", "/plugins/customer-insights", "menu.plugins.customerInsights", "enterprise.plugins.customer-insights.read")),
            List.of(new PluginConfiguration("customer-insights.label", "STRING", false, true),
                    new PluginConfiguration("customer-insights.segment", "STRING", false, true)),
            "/api/v1/enterprises/{enterpriseId}/plugins/customer-insights",
            List.of("customers", "system-parameters", "dictionaries"));

    @Override
    public PluginDescriptor descriptor() { return DESCRIPTOR; }
}
