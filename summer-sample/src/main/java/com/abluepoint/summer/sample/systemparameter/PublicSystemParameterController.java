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
    PublicSystemParameterView read(@PathVariable String key, Authentication auth) { try { return service.publicRead(key, auth); }
        catch (SystemParameterNotFoundException e) { throw new com.abluepoint.summer.common.error.SummerException(com.abluepoint.summer.common.error.CommonErrorCode.NOT_FOUND); } }
}
record PublicSystemParameterView(String key, Object value, long version) {}
