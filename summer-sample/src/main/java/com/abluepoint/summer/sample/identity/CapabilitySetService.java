package com.abluepoint.summer.sample.identity;

import org.springframework.security.core.Authentication;

interface CapabilitySetService {

    CapabilitySet platform(Authentication authentication);

    CapabilitySet enterprise(String enterpriseId, Authentication authentication);
}
