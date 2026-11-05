package com.abluepoint.summer.sample.systemparameter;
import com.abluepoint.summer.sample.configuration.ConfigurationValueValidator;
import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
import com.abluepoint.summer.common.error.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
@Component
class ParameterOverrideValidator implements ConfigurationValueValidator {
    private final SystemParameterRepository repository;
    ParameterOverrideValidator(SystemParameterRepository repository) { this.repository=repository; }
    public String resource() { return "system-parameters"; }
    private SystemParameterEntity base(String tenant,String key,Authentication auth, BusinessDataScopes.Filter scope) {
        return repository.findScoped(key,scope).orElseThrow(()->new SummerException(CommonErrorCode.NOT_FOUND));
    }
    public void requireBase(String tenant,String key,Authentication auth, BusinessDataScopes.Filter scope) { base(tenant,key,auth,scope); }
    public void validate(String tenant,String key,Object value,Authentication auth, BusinessDataScopes.Filter scope) {
        var row=base(tenant,key,auth,scope);
        var errors=SystemParameterService.validate(row.key(),row.type(),value,row.required(),row.min(),row.max(),row.pattern());
        if(!errors.isEmpty()) throw new SummerException(CommonErrorCode.INVALID_ARGUMENT,errors);
    }
}
