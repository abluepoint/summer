package com.abluepoint.summer.sample.systemparameter;
import com.abluepoint.summer.sample.configuration.ConfigurationValueValidator;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.common.error.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
@Component
class ParameterOverrideValidator implements ConfigurationValueValidator {
    private final SystemParameterRepository repository;
    private final DataScopeService scopes;
    ParameterOverrideValidator(SystemParameterRepository repository, DataScopeService scopes) { this.repository=repository;this.scopes=scopes; }
    public String resource() { return "system-parameters"; }
    private SystemParameterEntity base(String tenant,String key,Authentication auth) {
        return repository.findScoped(key,scopes.filter(tenant,resource(),auth)).orElseThrow(()->new SummerException(CommonErrorCode.NOT_FOUND));
    }
    public void requireBase(String tenant,String key,Authentication auth) { base(tenant,key,auth); }
    public void validate(String tenant,String key,Object value,Authentication auth) {
        var row=base(tenant,key,auth);
        var errors=SystemParameterService.validate(row.key(),row.type(),value,row.required(),row.min(),row.max(),row.pattern());
        if(!errors.isEmpty()) throw new SummerException(CommonErrorCode.INVALID_ARGUMENT,errors);
    }
}
