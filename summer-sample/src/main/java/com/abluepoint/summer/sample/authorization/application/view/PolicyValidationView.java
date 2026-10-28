package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record PolicyValidationView(String policyId, boolean valid, List<String> errors, List<String> warnings) {}
