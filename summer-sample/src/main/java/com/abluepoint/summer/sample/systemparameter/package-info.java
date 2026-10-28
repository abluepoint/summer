@org.springframework.modulith.ApplicationModule(
        displayName = "System parameters",
        allowedDependencies = {"customer::audit", "authorization::data-scope", "cache::cache", "configuration::overrides", "configuration::validation", "configuration::access", "configuration::context", "configuration::views", "configuration::commands", "configuration::requests", "resilience::reads", "resource::permissions"}
)
package com.abluepoint.summer.sample.systemparameter;
