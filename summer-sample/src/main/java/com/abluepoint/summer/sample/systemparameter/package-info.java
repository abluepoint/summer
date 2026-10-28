@org.springframework.modulith.ApplicationModule(
        displayName = "System parameters",
        allowedDependencies = {"customer::audit", "authorization", "cache::cache", "configuration", "resilience::reads", "resource"}
)
package com.abluepoint.summer.sample.systemparameter;
