package com.abluepoint.summer.sample.resource;

import jakarta.servlet.Filter;
import org.springframework.web.servlet.HandlerMapping;
import java.util.List;
import java.util.Optional;

/** Metadata-only adapters for custom runtime mappings/endpoint filters. Empty Optional means unsupported;
 * present empty list explicitly declares a supported component with no endpoints. Never execute handlers. */
public interface InterfaceResourceContributor {
    default Optional<List<InterfaceResourceDescriptor>> mapping(HandlerMapping mapping) { return Optional.empty(); }
    default Optional<List<InterfaceResourceDescriptor>> filter(Filter filter) { return Optional.empty(); }
}
