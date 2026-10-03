package com.abluepoint.summer.sample.authorization;

import java.util.List;

record CapabilityCreateRequest(String code, String resource, String action, String riskLevel, String scope) {}
record CapabilityStatusRequest(String status) {}
record RoleCreateRequest(String name, String code, List<String> capabilityIds, String bundleId) {}
record RoleUpdateRequest(String name, String code, String status, List<String> capabilityIds, String bundleId) {}
record RoleCapabilitiesRequest(List<String> capabilityIds) {}
record BundleCreateRequest(String name, String code, List<String> capabilityIds, String changeSummary) {}
record BundleUpdateRequest(String name, String code, String status, List<String> capabilityIds, String changeSummary) {}
record CapabilityView(String capabilityId, String enterpriseId, String scope, String code, String resource,
                     String action, String riskLevel, String status, long version) {}
record CapabilityCatalogView(String workspace, String enterpriseId, List<CapabilityView> capabilities, long version) {}
record RoleView(String roleId, String enterpriseId, String name, String code, boolean builtIn, String status,
                List<String> capabilityIds, long version, String bundleId) {}
record BundleView(String bundleId, String enterpriseId, String name, String code, String status,
                  List<String> capabilityIds, long version, String changeSummary) {}
record BundleVersionView(String bundleId, long version, List<String> capabilityIds, String changeSummary) {}
