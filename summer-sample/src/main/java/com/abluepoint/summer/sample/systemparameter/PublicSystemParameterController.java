package com.abluepoint.summer.sample.systemparameter;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.abluepoint.summer.mvc.annotation.Field;

@RestController
class PublicSystemParameterController {
    private final SystemParameterService service;
    PublicSystemParameterController(SystemParameterService service) { this.service = service; }
    @GetMapping({"/public/system-parameters/{key}", "/public/enterprises/{enterpriseId}/system-parameters/{key}"})
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param="key", style="systemParameterKeyStyle")
    PublicSystemParameterView read(@PathVariable String key, Authentication auth) {
        return service.publicRead(key, auth);
    }
}
record PublicSystemParameterView(String key, Object value, long version) {}
