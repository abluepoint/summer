package com.abluepoint.summer.sample.identity.application;

import org.springframework.security.core.Authentication;

public interface CapabilitySetService {

    CapabilitySetView platform(Authentication authentication);

    CapabilitySetView enterprise(String enterpriseId, Authentication authentication);
}
