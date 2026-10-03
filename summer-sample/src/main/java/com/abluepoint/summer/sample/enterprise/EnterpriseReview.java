package com.abluepoint.summer.sample.enterprise;

import java.util.List;

record EnterpriseReview(String applicationId, String enterpriseId, String legalName, String status, List<MaterialMetadata> materials, String revision) {
    EnterpriseReview { materials = List.copyOf(materials); }
}

record MaterialMetadata(String name, String mediaType, long size) {}
