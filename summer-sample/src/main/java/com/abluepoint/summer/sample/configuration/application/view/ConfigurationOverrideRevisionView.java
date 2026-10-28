package com.abluepoint.summer.sample.configuration.application.view;
import java.time.Instant;
@org.springframework.modulith.NamedInterface("views")
public record ConfigurationOverrideRevisionView(long revision,String state,Object value,boolean publicReadable,
        boolean enabled,String principal,Instant createdAt) {}
