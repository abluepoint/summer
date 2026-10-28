package com.abluepoint.summer.sample.enterprise.application;

import java.util.List;

public record EnterpriseReviewView(String applicationId, String enterpriseId, String legalName, String status, List<EnterpriseMaterialView> materials, String revision) {
    public EnterpriseReviewView { materials = List.copyOf(materials); }

    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "EnterpriseReview[" + "applicationId=" + applicationId + ", " + "enterpriseId=" + enterpriseId + ", " + "legalName=" + legalName + ", " + "status=" + status + ", " + "materials=" + materials + ", " + "revision=" + revision + "]"; }
}

