@org.springframework.modulith.ApplicationModule(
        displayName = "System parameters",
        allowedDependencies = {"customer::audit", "authorization", "cache", "configuration", "resilience", "resource"}
)
package com.abluepoint.summer.sample.systemparameter;
