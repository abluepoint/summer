package com.abluepoint.summer.sample.systemparameter.api;

import com.abluepoint.summer.sample.systemparameter.application.SystemParameterService;
import com.abluepoint.summer.sample.systemparameter.application.view.PublicSystemParameterView;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterNotFoundException;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.abluepoint.summer.mvc.parameter.api.Field;

@RestController
public class PublicSystemParameterController {
    private final SystemParameterService service;
    public PublicSystemParameterController(SystemParameterService service) { this.service = service; }
    @GetMapping({"/public/system-parameters/{key}", "/public/enterprises/{enterpriseId}/system-parameters/{key}"})
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param="key", style="systemParameterKeyStyle")
    public PublicSystemParameterView read(@PathVariable String key, Authentication auth) { try { return service.publicRead(key, auth); }
        catch (SystemParameterNotFoundException e) { throw new com.abluepoint.summer.common.error.api.SummerException(com.abluepoint.summer.common.error.api.CommonErrorCode.NOT_FOUND); } }
}
